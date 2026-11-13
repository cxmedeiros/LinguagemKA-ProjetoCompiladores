package ka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import ka.ast.Expr;
import ka.ast.FieldPattern;
import ka.ast.MatchCase;
import ka.ast.Pattern;
import ka.ast.Stmt;

/*
 * Executa a AST (padrao Visitor). Contrato com o Resolver (ver Resolver.java):
 *   - Block: Environment novo.
 *   - Funcao: UM Environment por chamada com "this" + parametros (KaFunction.call).
 *   - Switch: cada case/default em Environment proprio.
 *   - Match: cada case em Environment proprio com os bindings do padrao.
 *
 * Tipos de valor em tempo de execucao:
 *   nil -> null | Boolean | Integer (int) | Double (float) | String
 *   KaObject | KaCallable (KaFunction ou nativa)
 *
 * Regras numericas: int op int -> int (divisao trunca); se algum lado e
 * float, o resultado e float (o int e promovido).
 */
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    final Environment globals = new Environment();
    private Environment environment = globals;

    // Distancias calculadas pelo Resolver. Acumula entre execucoes para o
    // REPL manter um unico Interpreter (e as globais) vivo.
    private final Map<Expr, Integer> locals = new IdentityHashMap<>();

    // Modo --trace: mostra no stdout o que o interpretador esta fazendo
    // (linhas com prefixo [exec]). Desligado por padrao.
    boolean trace = false;
    private int depth = 0;   // profundidade de chamadas, so para indentar o trace

    Interpreter() {
        defineNatives();
    }

    private void trace(String message) {
        if (!trace) return;
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        System.out.println(indent + "[exec] " + message);
    }

    private String typeOf(Object v) {
        if (v == null) return "nil";
        if (v instanceof Integer) return "int";
        if (v instanceof Double) return "float";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof KaObject) return "object";
        if (v instanceof KaCallable) return "function";
        return v.getClass().getSimpleName();
    }

    // Valor + tipo, para o trace: 10:int, 10.0:float, "oi":string
    private String show(Object v) {
        String text = v instanceof String ? "\"" + v + "\"" : stringify(v);
        return text + ":" + typeOf(v);
    }

    private void defineNatives() {
        globals.define("clock", new KaCallable() {
            @Override public int arity() { return 0; }
            @Override public Object call(Interpreter interpreter, List<Object> arguments) {
                return System.currentTimeMillis() / 1000.0;
            }
            @Override public String toString() { return "<native fn clock>"; }
        });

        // Object.create(proto): novo objeto cujo prototipo e proto (ou nil).
        KaObject objectGlobal = new KaObject(null);
        objectGlobal.fields.put("create", new KaCallable() {
            @Override public int arity() { return 1; }
            @Override public Object call(Interpreter interpreter, List<Object> arguments) {
                Object proto = arguments.get(0);
                if (proto != null && !(proto instanceof KaObject)) {
                    // location null: visitCallExpr completa com a posicao da chamada.
                    throw new RuntimeError((SourceLocation) null,
                        "O prototipo de Object.create deve ser um objeto ou nil.");
                }
                return new KaObject((KaObject) proto);
            }
            @Override public String toString() { return "<native fn create>"; }
        });
        globals.define("Object", objectGlobal);
    }

    void interpret(List<Stmt> statements, Map<Expr, Integer> resolved) {
        locals.putAll(resolved);
        try {
            for (Stmt statement : statements) {
                if (trace) {
                    trace(">>> " + new AstPrinter().print(java.util.Collections.singletonList(statement)).trim());
                    if (statement instanceof Stmt.Expression) {
                        Object value = evaluate(((Stmt.Expression) statement).expression);
                        trace("=> " + show(value));
                        continue;
                    }
                }
                execute(statement);
            }
        } catch (RuntimeError error) {
            Ka.runtimeError(error);
        } catch (Return stray) {
            // O Resolver ja barra 'return' fora de funcao; so por seguranca.
        } catch (StackOverflowError overflow) {
            Ka.runtimeError(new RuntimeError((SourceLocation) null,
                "Estouro de pilha (recursao profunda demais)."));
        } finally {
            depth = 0;
            environment = globals;   // um erro no meio nao pode deixar escopo preso no REPL
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // Executa os comandos em 'env' e SEMPRE restaura o ambiente anterior
    // (inclusive quando um Return ou RuntimeError atravessa).
    void executeBlock(List<Stmt> statements, Environment env) {
        Environment previous = this.environment;
        try {
            this.environment = env;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private Object evaluateIn(Expr expr, Environment env) {
        Environment previous = this.environment;
        try {
            this.environment = env;
            return evaluate(expr);
        } finally {
            this.environment = previous;
        }
    }

    // ===================== Statements =====================

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        System.out.println(stringify(evaluate(stmt.expression)));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) value = evaluate(stmt.initializer);
        environment.define(stmt.name.name, value);
        trace("var " + stmt.name.name + " = " + show(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        environment.define(stmt.name.name,
            new KaFunction(stmt.name.name, stmt.params, stmt.body, environment));
        return null;
    }

    // Sem fallthrough: executa so o primeiro case que casar.
    @Override
    public Void visitSwitchStmt(Stmt.Switch stmt) {
        Object subject = evaluate(stmt.subject);

        for (int i = 0; i < stmt.caseValues.size(); i++) {
            if (isEqual(subject, evaluate(stmt.caseValues.get(i)))) {
                trace("switch " + show(subject) + ": casou o case #" + (i + 1));
                executeBlock(stmt.caseBodies.get(i), new Environment(environment));
                return null;
            }
        }
        trace("switch " + show(subject) + ": nenhum case casou" + (stmt.defaultBranch != null ? ", indo para default" : ""));
        if (stmt.defaultBranch != null) {
            executeBlock(stmt.defaultBranch, new Environment(environment));
        }
        return null;
    }

    // ===================== Expressions =====================

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Symbol name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) return environment.getAt(distance, name.name);
        return globals.get(name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        trace(expr.name.name + " = " + show(value));
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.kind) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                if (right instanceof Integer) {
                    int n = (Integer) right;
                    if (n == Integer.MIN_VALUE) {
                        throw new RuntimeError(expr.operator, "Estouro de inteiro.");
                    }
                    return -n;
                }
                return -(Double) right;
            default:
                return null;
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        Object result = binaryOp(expr.operator, left, right);
        trace(show(left) + " " + expr.operator + " " + show(right) + " -> " + show(result));
        return result;
    }

    private Object binaryOp(Operator op, Object left, Object right) {
        switch (op.kind) {
            case PLUS:
                if (isNumber(left) && isNumber(right)) return arithmetic(op, left, right);
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(op,
                    "Operandos de '+' devem ser dois numeros ou duas strings.");
            case MINUS:
            case STAR:
            case SLASH:
                checkNumberOperands(op, left, right);
                return arithmetic(op, left, right);
            case GREATER:
                checkNumberOperands(op, left, right);
                return compare(left, right) > 0;
            case GREATER_EQUAL:
                checkNumberOperands(op, left, right);
                return compare(left, right) >= 0;
            case LESS:
                checkNumberOperands(op, left, right);
                return compare(left, right) < 0;
            case LESS_EQUAL:
                checkNumberOperands(op, left, right);
                return compare(left, right) <= 0;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            default:
                return null;
        }
    }

    // int op int -> int ; qualquer lado float -> float.
    private Object arithmetic(Operator op, Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            int a = (Integer) left, b = (Integer) right;
            try {
                switch (op.kind) {
                    case PLUS:  return Math.addExact(a, b);
                    case MINUS: return Math.subtractExact(a, b);
                    case STAR:  return Math.multiplyExact(a, b);
                    case SLASH:
                        if (b == 0) throw new RuntimeError(op, "Divisao inteira por zero.");
                        if (a == Integer.MIN_VALUE && b == -1) {
                            throw new ArithmeticException();
                        }
                        return a / b;   // trunca em direcao a zero
                    default: break;
                }
            } catch (ArithmeticException overflow) {
                throw new RuntimeError(op, "Estouro de inteiro.");
            }
        }

        double a = toDouble(left), b = toDouble(right);
        switch (op.kind) {
            case PLUS:  return a + b;
            case MINUS: return a - b;
            case STAR:  return a * b;
            case SLASH: return a / b;   // float / 0 = Infinity/NaN, como em Java
            default:    throw new IllegalStateException("operador aritmetico inesperado: " + op);
        }
    }

    private int compare(Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            return Integer.compare((Integer) left, (Integer) right);
        }
        return Double.compare(toDouble(left), toDouble(right));
    }

    private double toDouble(Object o) {
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        return (Double) o;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.kind == Operator.Kind.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee;
        KaObject receiver = null;

        // obj.metodo(...): avalia 'obj' UMA vez e guarda como receptor do 'this'.
        if (expr.callee instanceof Expr.Get) {
            Expr.Get get = (Expr.Get) expr.callee;
            Object object = evaluate(get.object);
            if (!(object instanceof KaObject)) {
                throw new RuntimeError(get.name, "Somente objetos tem propriedades.");
            }
            receiver = (KaObject) object;
            callee = receiver.get(get.name);
        } else {
            callee = evaluate(expr.callee);
        }

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof KaCallable)) {
            throw new RuntimeError(expr.location, "So e possivel chamar funcoes.");
        }

        KaCallable function = (KaCallable) callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.location,
                "Esperado " + function.arity() + " argumento(s), recebeu "
                + arguments.size() + ".");
        }

        if (receiver != null && function instanceof KaFunction) {
            function = ((KaFunction) function).bind(receiver);
        }

        if (trace) {
            String name = expr.callee instanceof Expr.Variable ? ((Expr.Variable) expr.callee).name.name
                        : expr.callee instanceof Expr.Get ? ((Expr.Get) expr.callee).name.name
                        : "<expressao>";
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(show(arguments.get(i)));
            }
            trace("chamada " + name + "(" + args + ")"
                + (receiver != null && function instanceof KaFunction ? "  [this = objeto]" : ""));
        }

        depth++;
        try {
            Object result = function.call(this, arguments);
            depth--;
            trace("retorno " + show(result));
            return result;
        } catch (RuntimeError e) {
            depth--;
            // Funcoes nativas nao conhecem a posicao da chamada: completa aqui.
            if (e.location == null) throw new RuntimeError(expr.location, e.getMessage());
            throw e;
        }
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof KaObject) {
            return ((KaObject) object).get(expr.name);
        }
        throw new RuntimeError(expr.name, "Somente objetos tem propriedades.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);
        if (!(object instanceof KaObject)) {
            throw new RuntimeError(expr.name, "Somente objetos tem campos.");
        }
        Object value = evaluate(expr.value);
        ((KaObject) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new KaFunction(null, expr.params, expr.body, environment);
    }

    @Override
    public Object visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
        KaObject object = new KaObject(null);
        for (int i = 0; i < expr.keys.size(); i++) {
            Symbol key = expr.keys.get(i);
            object.set(key, evaluate(expr.values.get(i)));
        }
        return object;
    }

    // ===================== Pattern matching =====================

    @Override
    public Object visitMatchExpr(Expr.Match expr) {
        Object value = evaluate(expr.scrutinee);
        trace("match " + show(value));

        int caseNumber = 0;
        for (MatchCase matchCase : expr.cases) {
            caseNumber++;
            Map<String, Object> bindings = new HashMap<>();
            if (matches(matchCase.pattern, value, bindings)) {
                if (trace) {
                    StringBuilder b = new StringBuilder();
                    for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                        if (b.length() > 0) b.append(", ");
                        b.append(entry.getKey()).append(" = ").append(show(entry.getValue()));
                    }
                    trace("match: casou o case #" + caseNumber + " (" + matchCase.pattern.getClass().getSimpleName()
                        + ") bindings {" + b + "}");
                }
                Environment matchEnv = new Environment(environment);
                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                    matchEnv.define(entry.getKey(), entry.getValue());
                }
                return evaluateIn(matchCase.body, matchEnv);
            }
        }

        trace("match: nenhum case casou");
        throw new RuntimeError(expr.location,
            "Nenhum caso do match correspondeu ao valor " + stringify(value) + ".");
    }

    private boolean matches(Pattern pattern, Object value, Map<String, Object> bindings) {
        if (pattern instanceof Pattern.Wildcard) {
            return true;
        }

        if (pattern instanceof Pattern.Literal) {
            return isEqual(((Pattern.Literal) pattern).value, value);
        }

        if (pattern instanceof Pattern.TypePattern) {
            Pattern.TypePattern tp = (Pattern.TypePattern) pattern;
            boolean matched;
            switch (tp.typeName) {
                case "Number":   matched = isNumber(value); break;
                case "Int":      matched = value instanceof Integer; break;
                case "Float":    matched = value instanceof Double; break;
                case "String":   matched = value instanceof String; break;
                case "Boolean":  matched = value instanceof Boolean; break;
                case "Nil":      matched = value == null; break;
                case "Object":   matched = value instanceof KaObject; break;
                case "Function": matched = value instanceof KaCallable; break;
                default:         matched = false;
            }
            if (matched && tp.binding != null) bindings.put(tp.binding, value);
            return matched;
        }

        if (pattern instanceof Pattern.ObjectPattern) {
            if (!(value instanceof KaObject)) return false;
            KaObject object = (KaObject) value;

            for (FieldPattern field : ((Pattern.ObjectPattern) pattern).fields) {
                if (!object.has(field.name)) return false;
                Object fieldValue = object.lookup(field.name);

                if (field.subPattern != null && !matches(field.subPattern, fieldValue, bindings)) {
                    return false;
                }
                if (field.binding != null) bindings.put(field.binding, fieldValue);
            }
            return true;
        }

        return false;
    }

    // ===================== Helpers =====================

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (Boolean) object;
        return true;
    }

    private boolean isNumber(Object o) {
        return o instanceof Integer || o instanceof Double;
    }

    // Numeros comparam por valor apos promocao (10 == 10.0 e true), coerente
    // com < e >=. Os demais tipos usam equals (objetos/funcoes: identidade).
    private boolean isEqual(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (isNumber(a) && isNumber(b)) {
            if (a instanceof Integer && b instanceof Integer) return a.equals(b);
            return toDouble(a) == toDouble(b);
        }
        return a.equals(b);
    }

    private void checkNumberOperand(Operator operator, Object operand) {
        if (isNumber(operand)) return;
        throw new RuntimeError(operator, "Operando deve ser numero.");
    }

    private void checkNumberOperands(Operator operator, Object left, Object right) {
        if (isNumber(left) && isNumber(right)) return;
        throw new RuntimeError(operator, "Operandos devem ser numeros.");
    }

    // Double mantem o ".0" (6.0), Integer imprime sem ponto: e assim que
    // o teste 12-int-float distingue float de int.
    private String stringify(Object object) {
        if (object == null) return "nil";
        return object.toString();
    }
}
