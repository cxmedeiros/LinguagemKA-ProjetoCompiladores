# Testes da Ka

Suite de **testes funcionais** para o front-end da Ka (Scanner, Parser e AstPrinter).

Não usa JUnit nem nenhuma biblioteca externa. Cada teste é um programa `.ka` com um arquivo `.expected` ao lado, que contém a saída que o compilador deve produzir. Um script compila o projeto, roda cada programa e compara o resultado com o esperado.

---

## Como executar

Requisito: **JDK** instalado (`javac` no `PATH` ou `JAVA_HOME` configurado).

Na raiz do projeto, no PowerShell:

```powershell
.\tests\run-tests.ps1                 # todos os testes
.\tests\run-tests.ps1 -Filter switch  # só os casos cujo nome contém "switch"
```

Se a política de execução bloquear o script:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\run-tests.ps1
```

O script:

1. compila `Ka/` em `out/`;
2. executa `java -cp out ka.Ka <arquivo.ka>` para cada caso;
3. compara a saída com o `.expected`;
4. mostra `[OK]` ou `[FALHOU]` por caso (com esperado e obtido, em caso de falha) e um resumo no final.

Códigos de saída: `0` todos passaram, `1` algum teste falhou, `2` falha ao compilar ou JDK não encontrado.

---

## Estrutura

```text
tests/
├── run-tests.ps1
├── tokens/    # análise léxica
├── ast/       # parser + AstPrinter
└── errors/    # erros léxicos e sintáticos
```

Cada caso é um par:

```text
nome.ka          programa de entrada
nome.expected    saída esperada
```

---

## O que cada grupo compara

O `Ka.java` imprime `=== Tokens ===`, depois `=== Parser ===` com a AST e uma linha de resumo. Erros vão para o `stderr`. Cada grupo olha para uma parte dessa saída.

| Grupo | Parte comparada | Verificações extras |
|---|---|---|
| `tokens/` | Linhas entre `=== Tokens ===` e `=== Parser ===` (formato `TIPO lexema literal`) | nenhuma |
| `ast/` | Linhas depois de `=== Parser ===` (AST em S-expression + linha de resumo) | código de saída `0` e `stderr` vazio |
| `errors/` | `stderr` completo | código de saída `65` e nenhuma AST impressa |

Espaços e quebras de linha no final do texto são ignorados na comparação.

---

## Casos existentes

### `tokens/`

| Caso | Cobre |
|---|---|
| `basico` | declaração simples e comentário `//` |
| `numeros_e_strings` | `INT` vs `FLOAT`, strings, `3.` como `INT` + `DOT` |
| `operadores` | todos os operadores e pontuação |
| `palavras_reservadas` | todas as palavras reservadas; `class` e `super` como `IDENTIFIER` |

### `ast/`

| Caso | Cobre |
|---|---|
| `precedencia` | precedência e associatividade, unários, `and`/`or`, comparação e igualdade |
| `literais` | inteiro, float, string, `true`, `false`, `nil`, `var` sem inicializador |
| `atribuicao_e_objetos` | atribuição encadeada, literais de objeto, `Get`, `Set`, `this` |
| `funcoes` | função nomeada, anônima, `return` com e sem valor, chamada encadeada |
| `chamadas` | chamada sem argumentos, método com argumentos, `obj.metodo().campo` |
| `controle` | `if`/`else`, `else` pendente, `while`, bloco |
| `for_desugar` | `for` transformado em `block` + `while` (inclusive `for (;;)`) |
| `switch` | `case`, `default`, `switch` vazio, `default` sem comandos |
| `vazio` | programa sem declarações |

### `errors/`

| Caso | Mensagem esperada em |
|---|---|
| `lexico_caractere` | caractere inesperado (e prova que o parser não roda após erro léxico) |
| `lexico_string_aberta` | string sem aspas finais |
| `lexico_inteiro_grande` | inteiro fora do limite |
| `sintatico_expressao_faltando` | `var x = ;` |
| `sintatico_ponto_virgula` | `;` faltando no meio do arquivo |
| `sintatico_fim_de_arquivo` | `;` faltando no fim (`Erro no fim`) |
| `sintatico_recuperacao` | dois erros no mesmo arquivo (sincronização do parser) |
| `sintatico_default_duplicado` | mais de um `default` |
| `sintatico_alvo_invalido` | `1 = 2;` |
| `sintatico_objeto_virgula` | vírgula sobrando em `{ x: 1, }` |
| `sintatico_if_sem_parenteses` | `if x > 1)` |
| `sintatico_switch_corpo_invalido` | comando solto dentro do `switch` |
| `sintatico_case_sem_dois_pontos` | `case 1` sem `:` |
| `sintatico_parametro_invalido` | `fun f(1) {}` |
| `sintatico_parentese_aberto` | `print (1 + 2;` |

---

## Como adicionar um teste

1. Escolha o grupo (`tokens`, `ast` ou `errors`).
2. Crie `nome.ka` com o programa.
3. Crie `nome.expected` com a saída esperada:
   - `tokens`: copie as linhas de `=== Tokens ===`, sem o cabeçalho;
   - `ast`: copie as linhas depois de `=== Parser ===`, incluindo `Analise sintatica concluida...`;
   - `errors`: copie as linhas do `stderr`, no formato `[linha N] Erro em 'x': mensagem`.
4. Rode `.\tests\run-tests.ps1 -Filter nome`.

Confira manualmente que a saída gerada está **correta** antes de gravá-la como esperada; o teste só garante que ela não muda.

---

## Observações

- Os testes `lexico_string_aberta`, `sintatico_fim_de_arquivo` e `sintatico_ponto_virgula` dependem de o arquivo `.ka` terminar com uma quebra de linha, pois isso afeta o número da linha reportada.
- A suite cobre apenas Scanner, Parser e AstPrinter. Não há interpretador nesta etapa, então nenhum teste verifica a execução dos programas.
- A pasta `out/` é gerada pela compilação e não deve ser versionada.
