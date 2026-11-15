Você é um engenheiro de compiladores sênior. Vai implementar a geração de código de três endereços (TAC) para a linguagem Ka, um projeto de faculdade em Java (Scanner, Parser, Resolver, Interpreter). Você tem acesso ao repositório e pode compilar e rodar comandos.

OBJETIVO
Adicionar a flag `--tac` a Ka.java, que imprime o TAC do programa sem executá-lo, sem alterar nenhum comportamento existente.

ESPECIFICAÇÃO (fonte única da verdade; siga à risca nomes, formato, templates e ordem de numeração, porque os testes comparam texto exato)
<especificacao>
Parte 2 nao veio no texto original; os anexos disponiveis trazem exemplos conceituais e nao substituem os exemplos A a G nem o contrato detalhado citado aqui.
</especificacao>

ANTES DE ESCREVER CÓDIGO (Fase F0), leia e confirme com um resumo curto:
- tool/GenerateAst.java (a AST real), Ka/Resolver.java (escopos e locals()), Ka/Interpreter.java (semântica de referência), Ka/Ka.java, Ka/Parser.java (como `for` é reduzido), test/run-tests.sh e uma amostra de test/shelltest/*.t.
- Se a especificação contradisser o código real, PARE e pergunte. Não invente nós, campos ou métodos que não existem.

FASES (ao fim de cada uma: compile do zero, rode `bash test/run-tests.sh` e a suíte antiga; tudo deve continuar passando, e então relate)
F1 Infraestrutura: pacote ka.tac (Operand, Op, TacInstruction, TacFunction, TacProgram, TacBuilder, TacValidator), serialização e nomes. Teste unitário (main ou .t) que monta um programa à mão e confere o texto. API congelada ao fim.
F2 Expressões: Literal, Grouping, Variable, This, Assign, Binary, Unary, Logical, Get, Set, ObjectLiteral, com a regra de snapshot (2.7).
F3 Fluxo: Block, Var, Expression, Print, If, While, Switch. Testes de laços aninhados.
F4 Funções: Function (declaração e anônima), Call, Return, parâmetros, this, captures e upvalues, ordem de funções em pré-ordem.
F5 Match: padrões Literal, Wildcard, TypePattern, ObjectPattern com subpadrões.
F6 Integração: `--tac` em Ka.java (e REPL), validador sempre ativo, build atualizado (run-tests.sh, run-tests.ps1, README), testes dourados e de erro.
F7 (opcional, recomendada) VM de TAC (`--tac-run`) e comparação com o Interpreter nos test/casos que saem com 0.

REGRAS
1. Não altere o comportamento atual: execução normal, `--debug` e `--trace` ficam idênticos. Não edite Ka/ast/*.java à mão.
2. Expected dos testes: derive à mão pelos templates e confira linha a linha. NUNCA copie a saída do gerador como expected sem essa conferência. Os exemplos A a G da especificação devem virar testes e passar exatamente.
3. Java 8, sem dependências, comentários em português sem acento, arquivos novos em LF.
4. Mudanças pequenas e verificáveis. Sem otimizações (sem propagação de constantes, sem eliminação de código morto): o TAC deve ser previsível e literal.
5. Se um caso não estiver coberto pela especificação, decida pelo comportamento do Interpreter, registre a decisão e continue.

CRITÉRIOS DE ACEITAÇÃO
- Build limpo e `bash test/run-tests.sh` verde (os 46 atuais mais os novos).
- Todos os programas de test/casos com exit 0 geram TAC que o validador aceita.
- Os exemplos A a G batem byte a byte.
- Erros de scanner, parser e resolver: exit 65 e stdout vazio em `--tac`.

FORMATO DE CADA RELATÓRIO DE FASE
1) arquivos criados ou alterados; 2) comandos rodados e resultado (contagem de testes); 3) decisões tomadas fora da especificação; 4) riscos ou dúvidas; 5) próxima fase.

## Complemento usado para executar F1-F3

Os documentos `Analise_Atualizada_Ka_TAC_Primeiro.docx.pdf` e `Guia_Ka_Resolver_Interpreter_TAC (1).docx` descrevem a arquitetura e exemplos didaticos. Eles nao contem os templates completos da Parte 2 ou os exemplos A-G. Para tornar verificaveis as tres fases autorizadas, este contrato local registra as convencoes adotadas a partir dos exemplos do PDF e do comportamento do Interpreter presente no repositorio.

### Serializacao e operandos

- Temporarios: `t1`, `t2`, ...; rotulos: `L1`, `L2`, ...; ambos com contadores deterministas iniciados em 1 por programa.
- Constantes: `nil`, `true`, `false`, numeros em representacao Java e strings entre aspas com escapes para barra invertida, aspas, nova linha e retorno.
- Globais mantem o nome Ka. Locais recebem identificador TAC unico, como `x$1`, para evitar colisao entre bindings em blocos distintos.
- Instrucoes: `MOVE`, `BINARY`, `UNARY`, `IF_FALSE`, `IF_TRUE`, `GOTO`, `PARAM`, `CALL`, `RETURN`, `PRINT`, `CREATE_OBJECT`, `CHECK_OBJECT`, `GET_PROPERTY`, `SET_PROPERTY`, `MAKE_CLOSURE`, `CHECK_CALLABLE`, `CHECK_ARITY`, `TYPE_TEST`, `HAS_PROPERTY` e `MATCH_ERROR`. A instrucao `LABEL` serializa como `Lx:`.
- `TacProgram.toString()` escreve uma instrucao por linha e termina com nova linha; funcoes, quando implementadas, ficam separadas por uma linha em branco.
- Operacoes aritmeticas e comparacoes permanecem genericas. A IR nao assume tipos estaticos. A verificacao de receptor de propriedade em atribuicao e emitida antes de avaliar o valor, igual ao Interpreter.

### Semantica para F1-F3

- Expressoes devolvem um `Operand`; comandos emitem instrucoes. A ordem de avaliacao segue o Interpreter.
- Aritmetica preserva a AST e a precedencia: `print 2 + 3 * 4;` deve gerar `t1 = 3 * 4`, `t2 = 2 + t1`, `print t2`.
- Operandos variaveis do lado esquerdo sao copiados para temporario antes de traduzir o lado direito, para preservar leituras diante de atribuicoes com efeitos colaterais.
- `and` e `or` usam saltos de curto-circuito e retornam o valor do operando selecionado, pois a Ka retorna valores dos operandos, nao necessariamente booleanos.
- `switch` avalia o assunto uma vez, avalia valores de cases na ordem, executa o primeiro corpo correspondente e nao tem fallthrough. Cada corpo usa escopo local proprio.
- `Call`, funcoes, `Return` e `Match` ficam fora das fases F1-F3 e devem falhar explicitamente como recursos ainda nao suportados pelo gerador.

### Decisoes para F4-F6

- Cada funcao recebe nome interno `fnN_nome` em ordem pre-ordem. A funcao imprime `this = @this`, aliases dos parametros como `x$N = @parameter0` e captures `uN = @upvalueN` antes do corpo.
- `closure nome captures (a, b)` cria o valor funcional. Captures referenciam celulas mutaveis, nao copias; funcoes intermediarias encaminham upvalues para closures aninhadas.
- Chamadas avaliam callee e argumentos da esquerda para direita. Argumentos variaveis sao congelados antes de avaliar o proximo. `CHECK_CALLABLE` e `CHECK_ARITY` ocorrem depois de avaliar os argumentos, como no Interpreter. Chamadas `obj.m(...)` congelam e validam o receptor antes de avaliar argumentos e passam-no como `this`.
- `match` avalia o scrutinee uma vez. `TYPE_TEST`, `HAS_PROPERTY`, `GET_PROPERTY` e comparacao `==` controlam os padroes; bindings so sao escritos no ramo correspondente. `MATCH_ERROR` representa o erro quando nenhum caso casa.
- `--tac` nao combina com `--debug` ou `--trace`. No REPL TAC os prompts ficam ocultos para nao contaminar a saida; cada linha e traduzida sem execucao.

F7 (VM de TAC) continua opcional. A IR ainda nao tem executor; a equivalencia de resultados sera validada quando essa fase for solicitada.
