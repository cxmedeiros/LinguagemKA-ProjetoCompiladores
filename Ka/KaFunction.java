package ka;

import java.util.List;

/*
 * Funcao do usuario em tempo de execucao: o corpo em TAC + as variaveis
 * capturadas (Cell, compartilhadas com quem as declarou) + o receptor.
 * 'this' e vinculado na CHAMADA:  obj.m() -> this = obj;
 * var f = obj.m; f() -> this = nil.
 */
final class KaFunction implements KaCallable {
    final TacInterpreter.Code code;
    final Cell[] upvalues;
    final KaObject boundReceiver;   // null = sem 'this'

    KaFunction(TacInterpreter.Code code, Cell[] upvalues, KaObject boundReceiver) {
        this.code = code;
        this.upvalues = upvalues;
        this.boundReceiver = boundReceiver;
    }

    // Copia com 'this' fixado.
    KaFunction bind(KaObject receiver) {
        return new KaFunction(code, upvalues, receiver);
    }

    List<String> parameters() {
        return code.function.parameters();
    }

    @Override
    public int arity() {
        return parameters().size();
    }

    @Override
    public Object call(TacInterpreter interpreter, List<Object> arguments) {
        return interpreter.invoke(this, arguments);
    }

    @Override
    public String toString() {
        String name = code.function.sourceName();
        return "<fn " + (name != null ? name : "anonima") + ">";
    }
}
