package ka;

/*
 * Hoje guarda so a linha. A ideia e evoluir pra incluir coluna e arquivo
 */
public class SourceLocation {
    final int line;

    SourceLocation(int line) {
        this.line = line;
    }

    SourceLocation(Token token) {
        this(token.line);
    }

    @Override
    public String toString() {
        return "linha " + line;
    }
}
