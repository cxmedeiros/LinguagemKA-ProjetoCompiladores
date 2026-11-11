package ka;

import java.util.List;

/*
 * Tudo que pode ser chamado com (): funcoes do usuario (KaFunction) e
 * funcoes nativas. O Interpreter so conhece esta interface.
 */
interface KaCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}
