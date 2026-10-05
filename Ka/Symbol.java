package ka;

/*
 * Um Symbol representa o NOME de algo dentro da arvore de sintaxe (AST),
 * junto com a posicao (SourceLocation) onde ele foi declarado/referenciado: nome de variavel,
 * nome de funcao, nome de parametro, chave de um literal de objeto, ou a
 * palavra "this"/"return".
 * O construtor Symbol(Token) existe so para facilitar a conversao dentro
 * do Parser: o Parser recebe Token do Scanner e, ao montar um no da AST,
 * empacota so o que interessa num Symbol.
 */
public class Symbol {
    final String name;
    final SourceLocation location;

    Symbol(String name, SourceLocation location) {
        this.name = name;
        this.location = location;
    }

    // Conveniencia: constroi um Symbol diretamente a partir de um Token
    // (e o caminho mais comum, usado o tempo todo dentro do Parser).
    Symbol(Token token) {
        this(token.lexeme, new SourceLocation(token));
    }

    @Override
    public String toString() {
        return name;
    }
}
