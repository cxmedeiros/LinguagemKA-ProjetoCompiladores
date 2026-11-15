package ka.tac;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Verifica definicoes de rotulos e usos de temporarios em ordem. */
public final class TacValidator {
    public void validate(TacProgram program) {
        validateInstructions(program.instructions());
        for (TacFunction function : program.functions()) validateInstructions(function.instructions());
    }

    private void validateInstructions(List<TacInstruction> instructions) {
        Set<String> labels = new HashSet<>();
        Set<String> targets = new HashSet<>();
        Set<String> temporaries = new HashSet<>();
        for (TacInstruction instruction : instructions) {
            validateShape(instruction);
            if (instruction.op() == Op.LABEL) {
                Operand label = requireKind(instruction.result(), Operand.Kind.LABEL, "rotulo");
                if (!labels.add(label.render())) throw invalid("Rotulo duplicado: " + label.render());
            }
            if (instruction.op() == Op.GOTO) targets.add(requireKind(arg(instruction, 0), Operand.Kind.LABEL, "destino").render());
            if (instruction.op() == Op.IF_FALSE || instruction.op() == Op.IF_TRUE) {
                targets.add(requireKind(arg(instruction, 1), Operand.Kind.LABEL, "destino").render());
            }
            for (Operand operand : instruction.operands()) {
                if (operand.kind() == Operand.Kind.TEMPORARY && !temporaries.contains(operand.render())) {
                    throw invalid("Temporario usado antes da definicao: " + operand.render());
                }
            }
            if (instruction.result() != null && instruction.result().kind() == Operand.Kind.TEMPORARY
                && instruction.op() != Op.LABEL) temporaries.add(instruction.result().render());
        }
        for (String target : targets) if (!labels.contains(target)) throw invalid("Rotulo inexistente: " + target);
    }

    private void validateShape(TacInstruction instruction) {
        Op op = instruction.op();
        int count = instruction.operands().size();
        switch (op) {
            case LABEL:
                requireKind(instruction.result(), Operand.Kind.LABEL, "rotulo");
                requireCount(op, count, 0);
                break;
            case MOVE:
            case UNARY:
            case CREATE_OBJECT:
                requireResult(instruction);
                requireCount(op, count, op == Op.CREATE_OBJECT ? 0 : 1);
                if (op == Op.UNARY) requireDetail(instruction);
                break;
            case BINARY:
                requireResult(instruction);
                requireCount(op, count, 2);
                requireDetail(instruction);
                break;
            case IF_FALSE:
            case IF_TRUE:
                requireNoResult(instruction);
                requireCount(op, count, 2);
                requireKind(instruction.operands().get(1), Operand.Kind.LABEL, "destino");
                break;
            case GOTO:
                requireNoResult(instruction);
                requireCount(op, count, 1);
                requireKind(instruction.operands().get(0), Operand.Kind.LABEL, "destino");
                break;
            case PARAM:
            case PRINT:
            case CHECK_OBJECT:
            case CHECK_CALLABLE:
                requireNoResult(instruction);
                requireCount(op, count, 1);
                break;
            case CALL:
                if (count < 2 || count > 3) throw invalid("CALL exige callee, aridade e this opcional.");
                if (instruction.result() != null) requireResult(instruction);
                break;
            case RETURN:
                requireNoResult(instruction);
                if (count > 1) throw invalid("RETURN aceita no maximo um operando.");
                break;
            case GET_PROPERTY:
                requireResult(instruction);
                requireCount(op, count, 1);
                requireDetail(instruction);
                break;
            case SET_PROPERTY:
                requireResult(instruction);
                requireCount(op, count, 2);
                requireDetail(instruction);
                break;
            case MAKE_CLOSURE:
                requireResult(instruction);
                requireDetail(instruction);
                break;
            case CHECK_ARITY:
                requireNoResult(instruction);
                requireCount(op, count, 2);
                break;
            case TYPE_TEST:
            case HAS_PROPERTY:
                requireResult(instruction);
                requireCount(op, count, 1);
                requireDetail(instruction);
                break;
            case MATCH_ERROR:
                requireNoResult(instruction);
                requireCount(op, count, 1);
                break;
            default:
                throw invalid("Operacao TAC desconhecida: " + op);
        }
    }

    private void requireResult(TacInstruction instruction) {
        Operand result = instruction.result();
        if (result == null || (result.kind() != Operand.Kind.TEMPORARY
            && result.kind() != Operand.Kind.VARIABLE && result.kind() != Operand.Kind.UPVALUE)) {
            throw invalid(instruction.op() + " exige resultado temporario, variavel ou upvalue.");
        }
    }
    private void requireNoResult(TacInstruction instruction) {
        if (instruction.result() != null) throw invalid(instruction.op() + " nao aceita resultado.");
    }
    private void requireDetail(TacInstruction instruction) {
        if (instruction.detail() == null || instruction.detail().isEmpty()) {
            throw invalid(instruction.op() + " exige detalhe.");
        }
    }
    private void requireCount(Op op, int actual, int expected) {
        if (actual != expected) throw invalid(op + " exige " + expected + " operando(s).");
    }

    private Operand arg(TacInstruction instruction, int index) {
        if (instruction.operands().size() <= index) throw invalid("Operando ausente em " + instruction.op());
        return instruction.operands().get(index);
    }
    private Operand requireKind(Operand operand, Operand.Kind kind, String label) {
        if (operand == null || operand.kind() != kind) throw invalid("Operando invalido para " + label + ".");
        return operand;
    }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
