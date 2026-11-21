package ka.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Funcao TAC, reservada para a etapa de suporte a funcoes. */
public final class TacFunction {
    private final String name;
    private final List<String> parameters;
    private final List<String> upvalues;
    private final List<TacInstruction> instructions;
    private final String sourceName; // nome na Ka (null = anonima); nao e serializado

    public TacFunction(String name, List<String> parameters, List<TacInstruction> instructions) {
        this(name, parameters, Collections.<String>emptyList(), instructions);
    }

    public TacFunction(String name, List<String> parameters, List<String> upvalues,
                       List<TacInstruction> instructions) {
        this(name, parameters, upvalues, instructions, null);
    }

    public TacFunction(String name, List<String> parameters, List<String> upvalues,
                       List<TacInstruction> instructions, String sourceName) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Nome de funcao vazio.");
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.upvalues = Collections.unmodifiableList(new ArrayList<>(upvalues));
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
        this.sourceName = sourceName;
    }
    public String name() { return name; }
    public List<String> parameters() { return parameters; }
    public List<String> upvalues() { return upvalues; }
    public List<TacInstruction> instructions() { return instructions; }
    public String sourceName() { return sourceName; }

    String render() {
        StringBuilder out = new StringBuilder(name).append(":\n");
        out.append("this = @this\n");
        for (int i = 0; i < parameters.size(); i++) {
            out.append(parameters.get(i)).append(" = @parameter").append(i).append('\n');
        }
        for (int i = 0; i < upvalues.size(); i++) {
            out.append(upvalues.get(i)).append(" = @upvalue").append(i).append('\n');
        }
        for (TacInstruction instruction : instructions) out.append(instruction.render()).append('\n');
        return out.toString();
    }
}
