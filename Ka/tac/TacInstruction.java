package ka.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Instrucao imutavel; a lista de operandos e copiada na construcao. */
public final class TacInstruction {
    private final Op op;
    private final Operand result;
    private final List<Operand> operands;
    private final String detail;

    public TacInstruction(Op op, Operand result, List<Operand> operands, String detail) {
        if (op == null) throw new IllegalArgumentException("Operacao ausente.");
        if (operands == null) throw new IllegalArgumentException("Lista de operandos ausente.");
        this.op = op;
        this.result = result;
        this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
        this.detail = detail;
    }

    public static TacInstruction of(Op op, Operand result, String detail, Operand... operands) {
        List<Operand> values = new ArrayList<>();
        Collections.addAll(values, operands);
        return new TacInstruction(op, result, values, detail);
    }

    public Op op() { return op; }
    public Operand result() { return result; }
    public List<Operand> operands() { return operands; }
    public String detail() { return detail; }

    public String render() {
        switch (op) {
            case LABEL: return requireResult().render() + ":";
            case MOVE: return requireResult().render() + " = " + operand(0).render();
            case BINARY: return requireResult().render() + " = " + operand(0).render()
                + " " + requireDetail() + " " + operand(1).render();
            case UNARY: return requireResult().render() + " = " + requireDetail() + operand(0).render();
            case IF_FALSE: return "ifFalse " + operand(0).render() + " goto " + operand(1).render();
            case IF_TRUE: return "ifTrue " + operand(0).render() + " goto " + operand(1).render();
            case GOTO: return "goto " + operand(0).render();
            case PARAM: return "param " + operand(0).render();
            case CALL: {
                String call = (result == null ? "" : result.render() + " = ")
                    + "call " + operand(0).render() + ", " + operand(1).render();
                return operands.size() > 2 ? call + ", this " + operand(2).render() : call;
            }
            case RETURN: return operands.isEmpty() ? "return" : "return " + operand(0).render();
            case PRINT: return "print " + operand(0).render();
            case CREATE_OBJECT: return requireResult().render() + " = create_object";
            case CHECK_OBJECT: return "check_object " + operand(0).render();
            case GET_PROPERTY: return requireResult().render() + " = getprop " + operand(0).render()
                + "." + requireDetail();
            case SET_PROPERTY: return requireResult().render() + " = setprop " + operand(0).render()
                + "." + requireDetail() + ", " + operand(1).render();
            case MAKE_CLOSURE: {
                StringBuilder closure = new StringBuilder(requireResult().render())
                    .append(" = closure ").append(requireDetail());
                if (!operands.isEmpty()) {
                    closure.append(" captures (");
                    for (int i = 0; i < operands.size(); i++) {
                        if (i > 0) closure.append(", ");
                        closure.append(operands.get(i).render());
                    }
                    closure.append(')');
                }
                return closure.toString();
            }
            case CHECK_CALLABLE: return "check_callable " + operand(0).render();
            case CHECK_ARITY: return "check_arity " + operand(0).render() + ", " + operand(1).render();
            case TYPE_TEST: return requireResult().render() + " = is_type " + operand(0).render()
                + ", " + requireDetail();
            case HAS_PROPERTY: return requireResult().render() + " = hasprop " + operand(0).render()
                + "." + requireDetail();
            case MATCH_ERROR: return "match_error " + operand(0).render()
                + (detail == null ? "" : " at " + detail);
            default: throw new IllegalStateException("Operacao TAC sem serializador: " + op);
        }
    }

    private Operand requireResult() {
        if (result == null) throw new IllegalStateException(op + " exige resultado.");
        return result;
    }
    private String requireDetail() {
        if (detail == null || detail.isEmpty()) throw new IllegalStateException(op + " exige detalhe.");
        return detail;
    }
    private Operand operand(int index) {
        if (index >= operands.size()) throw new IllegalStateException(op + " sem operando " + index + ".");
        return operands.get(index);
    }
}
