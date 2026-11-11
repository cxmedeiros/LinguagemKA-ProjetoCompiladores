package ka;

import java.util.List;

import ka.ast.Stmt;

/*
 * Funcao do usuario: closure (ambiente onde foi DECLARADA) + receptor.
 * 'this' e vinculado na CHAMADA:  obj.m() -> this = obj;
 * var f = obj.m; f() -> this = nil.
 */
class KaFunction implements KaCallable {
    private final String name;              // null = anonima
    private final List<Symbol> params;
    private final List<Stmt> body;
    private final Environment closure;
    private final KaObject boundReceiver;   // null = sem 'this'

    KaFunction(String name, List<Symbol> params, List<Stmt> body, Environment closure) {
        this(name, params, body, closure, null);
    }

    private KaFunction(String name, List<Symbol> params, List<Stmt> body,
                       Environment closure, KaObject boundReceiver) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.closure = closure;
        this.boundReceiver = boundReceiver;
    }

    // Copia com 'this' fixado.
    KaFunction bind(KaObject receiver) {
        return new KaFunction(name, params, body, closure, receiver);
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // Contrato com o Resolver: UM ambiente por chamada com "this" + params.
        Environment environment = new Environment(closure);
        environment.define("this", boundReceiver);
        for (int i = 0; i < params.size(); i++) {
            environment.define(params.get(i).name, arguments.get(i));
        }

        try {
            interpreter.executeBlock(body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + (name != null ? name : "anonima") + ">";
    }
}
