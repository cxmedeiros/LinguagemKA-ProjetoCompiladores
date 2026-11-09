package ka;

class Token {
    final TokenType type;   // o tipo do token, ex: PLUS, STRING, IDENTIFIER...
    final String lexeme;    // o texto cru original, ex: "+", "\"oi\"", "idade"
    final Object literal;   // o valor já convertido, ex: 123.0 (double), "oi" (String)
                             // pra tokens que não são literais (tipo "+"), fica null
    final int line;         // em qual linha do código fonte esse token apareceu
    final int column;       // em qual coluna o token comeca

    Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
    }

    // Isso só serve pra quando a gente faz System.out.println(token),
    // pra imprimir ele de um jeito legível, tipo: "PLUS + null"
    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
