package ka;

/*
 * Excecao usada para implementar 'return'. Nao e um erro de verdade:
 * o Interpreter a lanca em visitReturnStmt e KaFunction.call a captura.
 * Sem stack trace (writableStackTrace=false) o throw fica barato.
 */
class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
