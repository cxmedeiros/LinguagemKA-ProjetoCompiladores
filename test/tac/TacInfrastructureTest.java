package ka.tac;

import java.util.Arrays;
import java.util.Collections;

/** Teste independente do modelo, serializador e validador TAC. */
public final class TacInfrastructureTest {
    public static void main(String[] args) {
        TacProgram program = new TacProgram(Arrays.asList(
            TacInstruction.of(Op.MOVE, Operand.temporary(1), null, Operand.constant(3)),
            TacInstruction.of(Op.BINARY, Operand.temporary(2), "+",
                Operand.constant(2), Operand.temporary(1)),
            TacInstruction.of(Op.PRINT, null, null, Operand.temporary(2))
        ), Collections.<TacFunction>emptyList());

        new TacValidator().validate(program);
        String expected = "t1 = 3\nt2 = 2 + t1\nprint t2\n";
        if (!expected.equals(program.toString())) {
            throw new AssertionError("Serializacao inesperada:\n" + program);
        }

        expectInvalid(new TacProgram(Arrays.asList(
            TacInstruction.of(Op.GOTO, null, null, Operand.label("L1"))
        ), Collections.<TacFunction>emptyList()));
        expectInvalid(new TacProgram(Arrays.asList(
            TacInstruction.of(Op.PRINT, null, null, Operand.temporary("t9"))
        ), Collections.<TacFunction>emptyList()));
    }

    private static void expectInvalid(TacProgram program) {
        try {
            new TacValidator().validate(program);
            throw new AssertionError("O validador aceitou um programa TAC invalido.");
        } catch (IllegalArgumentException expected) {
            // O programa invalido foi rejeitado como esperado.
        }
    }
}
