package ka;

import java.util.List;

/*
 * Tudo que pode ser chamado com (): funcoes do usuario (KaFunction) e
 * funcoes nativas. O TacInterpreter so conhece esta interface.
 */
interface KaCallable {
    int arity();
    Object call(TacInterpreter interpreter, List<Object> arguments);
}
