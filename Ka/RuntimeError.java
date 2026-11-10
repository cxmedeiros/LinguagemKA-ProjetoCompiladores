package ka;

/*
 * Erro detectado durante a EXECUCAO (ex: "10 / \"oi\"", variavel indefinida).
 *
 * Guarda uma SourceLocation em vez de um Token: a AST nao carrega Tokens,
 * so Symbol, Operator e SourceLocation. O Interpreter pega a localizacao
 * de quem estiver a mao (operador, nome, parenteses da chamada).
 */
public class RuntimeError extends RuntimeException {
    final SourceLocation location;

    RuntimeError(SourceLocation location, String message) {
        super(message);
        this.location = location;
    }

    RuntimeError(Symbol symbol, String message) {
        this(symbol.location, message);
    }

    RuntimeError(Operator operator, String message) {
        this(operator.location, message);
    }
}
