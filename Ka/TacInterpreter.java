package ka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ka.tac.Op;
import ka.tac.Operand;
import ka.tac.TacFunction;
import ka.tac.TacInstruction;
import ka.tac.TacProgram;

/*
 * Executa um TacProgram: percorre as instrucoes com um "pc" (contador de
 * instrucao) e faz um switch sobre a operacao. Nao ha ambiente em cadeia:
 *
 *   - o Resolver + TacBuilder ja decidiram, antes da execucao, qual
 *     declaracao cada nome usa (x$1, x$2...), entao o nome basta;
 *   - cada CHAMADA tem um Frame proprio com seus temporarios (t1, t2...) e
 *     locais (x$N);
 *   - uma variavel capturada por closure vive numa Cell compartilhada entre
 *     o Frame que a declarou e as closures (operandos u0, u1...);
 *   - globais ficam num unico mapa; o operando sem '$' e global.
 *
 * Tipos de valor e regras de operadores: ver Values.
 */
final class TacInterpreter {

    // Funcao TAC pronta para executar: instrucoes em vetor + rotulo -> indice.
    static final class Code {
        final TacFunction function;               // null = programa principal
        final TacInstruction[] instructions;
        final Map<String, Integer> labels = new HashMap<>();
        final Operator.Kind[] operators;          // operador de BINARY/UNARY, convertido uma so vez
        final Map<String, Code> module;           // funcoes do MESMO TacProgram, por nome

        Code(TacFunction function, List<TacInstruction> list, Map<String, Code> module) {
            this.function = function;
            this.instructions = list.toArray(new TacInstruction[0]);
            this.operators = new Operator.Kind[instructions.length];
            this.module = module;
            for (int i = 0; i < instructions.length; i++) {
                TacInstruction in = instructions[i];
                if (in.op() == Op.LABEL) labels.put(in.result().render(), i);
                if (in.op() == Op.BINARY || in.op() == Op.UNARY) {
                    operators[i] = Operator.kindOfLexeme(in.detail());
                }
            }
        }
    }

    // Uma chamada em andamento.
    private static final class Frame {
        final Map<String, Object> temps = new HashMap<>();   // t1, t2...
        final Map<String, Cell> vars = new HashMap<>();      // x$N e "this"
        final List<Object> params = new ArrayList<>();       // PARAMs ainda nao consumidos por um CALL
        final Cell[] upvalues;                                // u0, u1...

        Frame(Cell[] upvalues) {
            this.upvalues = upvalues;
        }
    }

    // Variaveis globais. Instancia unica: no prompt (REPL) elas sobrevivem entre linhas.
    private final Map<String, Object> globals = new HashMap<>();

    // Modo --trace: mostra no stdout o que o executor esta fazendo (linhas com prefixo [exec]).
    boolean trace = false;
    private int depth = 0;   // profundidade de chamadas, so para indentar o trace

    TacInterpreter() {
        defineNatives();
    }

    private void defineNatives() {
        globals.put("clock", new KaCallable() {
            @Override public int arity() { return 0; }
            @Override public Object call(TacInterpreter interpreter, List<Object> arguments) {
                return System.currentTimeMillis() / 1000.0;
            }
            @Override public String toString() { return "<native fn clock>"; }
        });

        // Object.create(proto): novo objeto cujo prototipo e proto (ou nil).
        KaObject objectGlobal = new KaObject(null);
        objectGlobal.fields.put("create", new KaCallable() {
            @Override public int arity() { return 1; }
            @Override public Object call(TacInterpreter interpreter, List<Object> arguments) {
                Object proto = arguments.get(0);
                if (proto != null && !(proto instanceof KaObject)) {
                    // location null: a instrucao CALL completa com a posicao da chamada.
                    throw new RuntimeError((SourceLocation) null,
                        "O prototipo de Object.create deve ser um objeto ou nil.");
                }
                return new KaObject((KaObject) proto);
            }
            @Override public String toString() { return "<native fn create>"; }
        });
        globals.put("Object", objectGlobal);
    }

    void run(TacProgram program) {
        Map<String, Code> module = new HashMap<>();
        for (TacFunction function : program.functions()) {
            module.put(function.name(), new Code(function, function.instructions(), module));
        }
        Code main = new Code(null, program.instructions(), module);

        try {
            execute(main, new Frame(new Cell[0]));
        } catch (RuntimeError error) {
            Ka.runtimeError(error);
        } catch (StackOverflowError overflow) {
            Ka.runtimeError(new RuntimeError((SourceLocation) null,
                "Estouro de pilha (recursao profunda demais)."));
        } finally {
            depth = 0;   // um erro no meio nao pode deixar o trace desalinhado no REPL
        }
    }

    // Chamada de uma funcao do usuario (KaFunction.call chega aqui).
    Object invoke(KaFunction function, List<Object> arguments) {
        Frame frame = new Frame(function.upvalues);
        frame.vars.put("this", new Cell(function.boundReceiver));
        List<String> parameters = function.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            frame.vars.put(parameters.get(i), new Cell(arguments.get(i)));
        }
        return execute(function.code, frame);
    }

    // ===================== Operandos =====================

    // Local = x$N (a Ka nao aceita '$' em identificadores) ou o 'this' da chamada.
    private static boolean isLocal(String name) {
        return name.indexOf('$') >= 0 || name.equals("this");
    }

    private static Cell cell(Frame frame, String name) {
        Cell cell = frame.vars.get(name);
        if (cell == null) {
            cell = new Cell(null);
            frame.vars.put(name, cell);
        }
        return cell;
    }

    private static Cell upvalueCell(Frame frame, Operand operand) {
        return frame.upvalues[Integer.parseInt(operand.render().substring(1))];
    }

    private Object read(Frame frame, Operand operand) {
        switch (operand.kind()) {
            case CONSTANT:  return operand.value();
            case TEMPORARY: return frame.temps.get(operand.render());
            case UPVALUE:   return upvalueCell(frame, operand).value;
            default: {
                String name = operand.render();
                if (isLocal(name)) return cell(frame, name).value;
                if (!globals.containsKey(name)) throw undefined(operand);
                return globals.get(name);
            }
        }
    }

    // 'declaration' = MOVE que declara a variavel (var, function, binding de match).
    private void write(Frame frame, Operand operand, Object value, boolean declaration) {
        switch (operand.kind()) {
            case TEMPORARY: frame.temps.put(operand.render(), value); return;
            case UPVALUE:   upvalueCell(frame, operand).value = value; return;
            default: {
                String name = operand.render();
                if (isLocal(name)) {
                    if (declaration) frame.vars.put(name, new Cell(value));   // variavel NOVA
                    else cell(frame, name).value = value;
                } else if (declaration) {
                    globals.put(name, value);                                 // define (ou redefine)
                } else {
                    if (!globals.containsKey(name)) throw undefined(operand);
                    globals.put(name, value);
                }
            }
        }
    }

    private static RuntimeError undefined(Operand operand) {
        return new RuntimeError(operand.location(),
            "Variavel indefinida '" + operand.render() + "'.");
    }

    // ===================== Laco principal =====================

    private Object execute(Code code, Frame frame) {
        TacInstruction[] instructions = code.instructions;
        int pc = 0;

        while (pc < instructions.length) {
            TacInstruction in = instructions[pc];
            List<Operand> a = in.operands();
            int current = pc++;

            switch (in.op()) {
                case LABEL:
                    break;

                case MOVE: {
                    Object value = read(frame, a.get(0));
                    write(frame, in.result(), value, in.isDeclaration());
                    if (trace && in.result().kind() == Operand.Kind.VARIABLE) {
                        trace((in.isDeclaration() ? "var " : "") + sourceName(in.result()) + " = "
                            + Values.show(value));
                    }
                    break;
                }

                case BINARY: {
                    Object left = read(frame, a.get(0));
                    Object right = read(frame, a.get(1));
                    Object result = Values.binary(code.operators[current], in.location(), left, right);
                    write(frame, in.result(), result, false);
                    if (trace) {
                        trace(Values.show(left) + " " + in.detail() + " " + Values.show(right)
                            + " -> " + Values.show(result));
                    }
                    break;
                }

                case UNARY: {
                    Object operand = read(frame, a.get(0));
                    Object result = Values.unary(code.operators[current], in.location(), operand);
                    write(frame, in.result(), result, false);
                    if (trace) trace(in.detail() + Values.show(operand) + " -> " + Values.show(result));
                    break;
                }

                case IF_FALSE:
                    if (!Values.isTruthy(read(frame, a.get(0)))) pc = code.labels.get(a.get(1).render());
                    break;

                case IF_TRUE:
                    if (Values.isTruthy(read(frame, a.get(0)))) pc = code.labels.get(a.get(1).render());
                    break;

                case GOTO:
                    pc = code.labels.get(a.get(0).render());
                    break;

                case PARAM:
                    frame.params.add(read(frame, a.get(0)));
                    break;

                case CHECK_CALLABLE:
                    if (!(read(frame, a.get(0)) instanceof KaCallable)) {
                        throw new RuntimeError(in.location(), "So e possivel chamar funcoes.");
                    }
                    break;

                case CHECK_ARITY: {
                    int expected = ((KaCallable) read(frame, a.get(0))).arity();
                    int received = (Integer) a.get(1).value();
                    if (expected != received) {
                        throw new RuntimeError(in.location(),
                            "Esperado " + expected + " argumento(s), recebeu " + received + ".");
                    }
                    break;
                }

                case CALL: {
                    Object result = call(frame, in);
                    if (in.result() != null) write(frame, in.result(), result, false);
                    break;
                }

                case RETURN: {
                    Object value = a.isEmpty() ? null : read(frame, a.get(0));
                    if (trace) trace("retorno " + Values.show(value));
                    return value;
                }

                case PRINT:
                    System.out.println(Values.stringify(read(frame, a.get(0))));
                    break;

                case CREATE_OBJECT:
                    write(frame, in.result(), new KaObject(null), false);
                    break;

                case CHECK_OBJECT:
                    if (!(read(frame, a.get(0)) instanceof KaObject)) {
                        // detail escolhe a mensagem: escrita de campo x acesso a propriedade/metodo.
                        throw new RuntimeError(in.location(), "campos".equals(in.detail())
                            ? "Somente objetos tem campos."
                            : "Somente objetos tem propriedades.");
                    }
                    break;

                case GET_PROPERTY: {
                    Object object = read(frame, a.get(0));
                    if (!(object instanceof KaObject)) {
                        throw new RuntimeError(in.location(), "Somente objetos tem propriedades.");
                    }
                    write(frame, in.result(),
                        ((KaObject) object).get(new Symbol(in.detail(), in.location())), false);
                    break;
                }

                case SET_PROPERTY: {
                    KaObject object = (KaObject) read(frame, a.get(0));
                    write(frame, in.result(),
                        object.set(new Symbol(in.detail(), in.location()), read(frame, a.get(1))), false);
                    break;
                }

                case HAS_PROPERTY:
                    write(frame, in.result(), ((KaObject) read(frame, a.get(0))).has(in.detail()), false);
                    break;

                case TYPE_TEST: {
                    Object value = read(frame, a.get(0));
                    boolean matched = Values.typeTest(in.detail(), value);
                    write(frame, in.result(), matched, false);
                    if (trace) trace("is_type " + Values.show(value) + ", " + in.detail()
                        + " -> " + Values.show(matched));
                    break;
                }

                case MAKE_CLOSURE: {
                    Cell[] captured = new Cell[a.size()];
                    for (int i = 0; i < captured.length; i++) captured[i] = capturedCell(frame, a.get(i));
                    write(frame, in.result(),
                        new KaFunction(code.module.get(in.detail()), captured, null), false);
                    break;
                }

                case MATCH_ERROR: {
                    if (trace) trace("match: nenhum case casou");
                    throw new RuntimeError(in.location(),
                        "Nenhum caso do match correspondeu ao valor "
                        + Values.stringify(read(frame, a.get(0))) + ".");
                }

                default:
                    throw new IllegalStateException("Operacao TAC sem execucao: " + in.op());
            }
        }
        return null;
    }

    // Variavel que uma closure vai compartilhar: a Cell ja existente na funcao que a declarou.
    private static Cell capturedCell(Frame frame, Operand source) {
        if (source.kind() == Operand.Kind.UPVALUE) return upvalueCell(frame, source);
        return cell(frame, source.render());
    }

    // CALL callee, argc [, this receptor]: consome os PARAMs pendentes e chama.
    private Object call(Frame frame, TacInstruction in) {
        List<Operand> a = in.operands();
        KaCallable function = (KaCallable) read(frame, a.get(0));
        int argc = (Integer) a.get(1).value();

        List<Object> arguments = new ArrayList<>(
            frame.params.subList(frame.params.size() - argc, frame.params.size()));
        for (int i = 0; i < argc; i++) frame.params.remove(frame.params.size() - 1);

        boolean bound = false;
        if (a.size() > 2 && function instanceof KaFunction) {
            function = ((KaFunction) function).bind((KaObject) read(frame, a.get(2)));
            bound = true;
        }

        if (trace) {
            StringBuilder shown = new StringBuilder();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) shown.append(", ");
                shown.append(Values.show(arguments.get(i)));
            }
            trace("chamada " + function + "(" + shown + ")" + (bound ? "  [this = objeto]" : ""));
        }

        depth++;
        try {
            return function.call(this, arguments);
        } catch (RuntimeError e) {
            // Funcoes nativas nao conhecem a posicao da chamada: completa aqui.
            if (e.location == null) throw new RuntimeError(in.location(), e.getMessage());
            throw e;
        } finally {
            depth--;
        }
    }

    // ===================== --trace =====================

    // x$3 -> x (o nome que o programador escreveu).
    private static String sourceName(Operand variable) {
        String name = variable.render();
        int mark = name.indexOf('$');
        return mark < 0 ? name : name.substring(0, mark);
    }

    private void trace(String message) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        System.out.println(indent + "[exec] " + message);
    }
}
