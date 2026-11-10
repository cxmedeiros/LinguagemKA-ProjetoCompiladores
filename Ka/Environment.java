package ka;

import java.util.HashMap;
import java.util.Map;

/*
 * Escopo em tempo de execucao: mapeia nomes para valores e aponta para o
 * escopo envolvente. O escopo global e o unico com enclosing == null.
 *
 * Variaveis locais sao acessadas por distancia (getAt/assignAt), calculada
 * antes pelo Resolver; so as globais usam busca dinamica (get/assign).
 */
class Environment {
    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        this(null);
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // Cria (ou, no escopo global, redefine) uma variavel neste escopo.
    void define(String name, Object value) {
        values.put(name, value);
    }

    // Busca dinamica subindo a cadeia de escopos.
    Object get(Symbol name) {
        if (values.containsKey(name.name)) return values.get(name.name);
        if (enclosing != null) return enclosing.get(name);
        throw undefined(name);
    }

    void assign(Symbol name, Object value) {
        if (values.containsKey(name.name)) {
            values.put(name.name, value);
        } else if (enclosing != null) {
            enclosing.assign(name, value);
        } else {
            throw undefined(name);
        }
    }

    // Acesso direto ao escopo "distance" niveis acima.
    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Symbol name, Object value) {
        ancestor(distance).values.put(name.name, value);
    }

    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return environment;
    }

    private static RuntimeError undefined(Symbol name) {
        return new RuntimeError(name, "Variavel indefinida '" + name.name + "'.");
    }
}
