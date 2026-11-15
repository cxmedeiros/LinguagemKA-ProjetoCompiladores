package ka.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Programa TAC composto por instrucoes top-level e funcoes. */
public final class TacProgram {
    private final List<TacInstruction> instructions;
    private final List<TacFunction> functions;

    public TacProgram(List<TacInstruction> instructions, List<TacFunction> functions) {
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
        this.functions = Collections.unmodifiableList(new ArrayList<>(functions));
    }
    public List<TacInstruction> instructions() { return instructions; }
    public List<TacFunction> functions() { return functions; }

    @Override public String toString() {
        StringBuilder out = new StringBuilder();
        for (TacInstruction instruction : instructions) out.append(instruction.render()).append('\n');
        for (TacFunction function : functions) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            if (out.length() > 0) out.append('\n');
            out.append(function.render());
        }
        return out.toString();
    }
}
