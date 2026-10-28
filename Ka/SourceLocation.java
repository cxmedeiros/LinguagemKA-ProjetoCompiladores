
package ka;

/*
 * Guarda a linha e a coluna inicial de um elemento no codigo fonte.
 * Futuramente, pode ser expandida para incluir arquivo e outras informacoes.
 */

public class SourceLocation {
    final int line;
    final int column;

    SourceLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    SourceLocation(Token token) {
        this(token.line, token.column);
    }

    @Override
    public String toString() {
        return "linha " + line + ", coluna " + column;
    }
}
