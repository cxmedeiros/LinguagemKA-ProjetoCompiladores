package ka;

/*
 * Variavel local em tempo de execucao do TAC.
 *
 * Uma closure NAO copia as variaveis que captura: ela guarda a propria Cell.
 * Por isso a funcao que declarou a variavel e as closures que a capturam
 * enxergam as mudancas umas das outras (ex.: um contador).
 * Cada execucao de uma declaracao (var, function, binding de match) cria uma
 * Cell nova; assim, duas iteracoes de um laco nao compartilham a variavel.
 */
final class Cell {
    Object value;

    Cell(Object value) {
        this.value = value;
    }
}
