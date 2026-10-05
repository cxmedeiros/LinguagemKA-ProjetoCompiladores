package ka;

/*
 * Um Symbol representa o NOME de algo dentro da arvore de sintaxe (AST),
 * junto com a linha onde ele foi declarado/referenciado: nome de variavel,
 * nome de funcao, nome de parametro, chave de um literal de objeto, ou a
 * palavra "this"/"return".
 *
 * O construtor Symbol(Token) existe so para facilitar a conversao dentro
 * do Parser: o Parser recebe Token do Scanner e, ao montar um no da AST,
 * empacota so o que interessa num Symbol.
 */
public class Symbol {
    final String name;
    final int line;

    Symbol(String name, int line) {
        this.name = name;
        this.line = line;
    }

    // Conveniencia: constroi um Symbol diretamente a partir de um Token
    // (e o caminho mais comum, usado o tempo todo dentro do Parser).
    Symbol(Token token) {
        this(token.lexeme, token.line);
    }

    @Override
    public String toString() {
        return name;
    }
}
