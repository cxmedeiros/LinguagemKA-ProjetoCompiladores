package ka.tac;

import java.util.Objects;

/** Operando tipado usado pelas instrucoes de TAC. */
public final class Operand {
    public enum Kind { CONSTANT, VARIABLE, TEMPORARY, LABEL, UPVALUE }

    private final Kind kind;
    private final Object value;
    private ka.SourceLocation location; // so para leituras de globais; fora de equals/hashCode

    private Operand(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    public static Operand constant(Object value) {
        if (value != null && !(value instanceof String) && !(value instanceof Boolean)
            && !(value instanceof Integer) && !(value instanceof Double)) {
            throw new IllegalArgumentException("Tipo de constante TAC nao suportado: "
                + value.getClass().getName());
        }
        return new Operand(Kind.CONSTANT, value);
    }

    public static Operand variable(String name) {
        return new Operand(Kind.VARIABLE, requireName(name));
    }

    public static Operand temporary(int number) {
        if (number < 1) throw new IllegalArgumentException("Numero de temporario invalido.");
        return new Operand(Kind.TEMPORARY, "t" + number);
    }

    public static Operand temporary(String name) {
        return new Operand(Kind.TEMPORARY, requireName(name));
    }

    public static Operand label(String name) {
        return new Operand(Kind.LABEL, requireName(name));
    }

    public static Operand upvalue(int index) {
        if (index < 0) throw new IllegalArgumentException("Indice de upvalue invalido.");
        return new Operand(Kind.UPVALUE, "u" + index);
    }

    /** Copia deste operando carregando a posicao no codigo-fonte (para "Variavel indefinida"). */
    public Operand at(ka.SourceLocation where) {
        if (where == null) return this;
        Operand copy = new Operand(kind, value);
        copy.location = where;
        return copy;
    }

    public ka.SourceLocation location() { return location; }
    public Kind kind() { return kind; }
    public Object value() { return value; }

    public String render() {
        if (kind == Kind.CONSTANT) return renderConstant(value);
        return value.toString();
    }

    private static String requireName(String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Nome vazio.");
        return name;
    }

    private static String renderConstant(Object value) {
        if (value == null) return "nil";
        if (value instanceof String) {
            String text = (String) value;
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        return value.toString();
    }

    @Override public String toString() { return render(); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Operand)) return false;
        Operand that = (Operand) other;
        return kind == that.kind && Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(kind, value); }
}
