package ka;

import java.util.HashMap;
import java.util.Map;

/*
 * Modelo prototipico: so existe OBJETO = campos + ponteiro para o
 * prototipo. Se o objeto nao tem o campo, a busca sobe pela cadeia.
 * Metodos sao campos cujo valor e uma funcao; o 'this' e vinculado pelo
 * Interpreter na hora da chamada (visitCallExpr).
 */
class KaObject {
    KaObject prototype;
    final Map<String, Object> fields = new HashMap<>();

    KaObject(KaObject prototype) {
        this.prototype = prototype;
    }

    // true se o campo existe neste objeto ou em algum prototipo.
    boolean has(String name) {
        if (name.equals("__proto__")) return true;
        for (KaObject o = this; o != null; o = o.prototype) {
            if (o.fields.containsKey(name)) return true;
        }
        return false;
    }

    // Valor do campo (assume has(name) == true).
    Object lookup(String name) {
        if (name.equals("__proto__")) return prototype;
        for (KaObject o = this; o != null; o = o.prototype) {
            if (o.fields.containsKey(name)) return o.fields.get(name);
        }
        return null;
    }

    Object get(Symbol name) {
        if (!has(name.name)) {
            throw new RuntimeError(name, "Propriedade indefinida '" + name.name + "'.");
        }
        return lookup(name.name);
    }

    // Escreve SEMPRE no proprio objeto, nunca no prototipo.
    Object set(Symbol name, Object value) {
        if (name.name.equals("__proto__")) {
            if (value == null || value instanceof KaObject) {
                this.prototype = (KaObject) value;
                return value;
            }
            throw new RuntimeError(name, "__proto__ deve ser um objeto ou nil.");
        }
        fields.put(name.name, value);
        return value;
    }

    @Override
    public String toString() {
        return "<object>";
    }
}
