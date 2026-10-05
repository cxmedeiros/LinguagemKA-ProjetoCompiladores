# Ka Language — Scanner, Parser e AST

A **Ka** é uma linguagem de programação desenvolvida em Java como projeto da disciplina de Compiladores.

Nesta etapa do projeto, implementamos o **front-end da linguagem até a análise sintática**, incluindo:

- análise léxica com Scanner/Lexer;
- geração de tokens;
- análise sintática com Parser descendente recursivo;
- construção da Árvore Sintática Abstrata (AST);
- impressão da AST para visualização e depuração;
- tratamento de erros léxicos e sintáticos.

A arquitetura utiliza como referência conceitos apresentados em *Crafting Interpreters*, de Robert Nystrom, com extensões e adaptações feitas para a linguagem Ka.

---

## Fluxo do projeto

Atualmente, o processamento da Ka segue este fluxo:

```text
Código-fonte (.ka)
        ↓
      Scanner
        ↓
   List<Token>
        ↓
      Parser
        ↓
       AST
        ↓
   AstPrinter
        ↓
Representação textual da AST
```

O projeto atual termina na construção e visualização da AST. A execução semântica completa da linguagem não faz parte desta etapa.

---

# 1. Scanner / Lexer

O `Scanner` é responsável pela **análise léxica**.

Ele percorre o código-fonte caractere por caractere e agrupa esses caracteres em unidades chamadas **tokens**.

Por exemplo:

```ka
var x = 10 + 5 * 2;
```

gera tokens equivalentes a:

```text
VAR
IDENTIFIER
EQUAL
INT
PLUS
INT
STAR
INT
SEMICOLON
EOF
```

Cada token é representado pela classe `Token` e possui:

```text
type
lexeme
literal
line
```

Onde:

- `type` representa a categoria do token;
- `lexeme` contém o texto original encontrado no código;
- `literal` contém o valor convertido, quando aplicável;
- `line` indica a linha do código-fonte em que o token foi encontrado.

Por exemplo, para:

```ka
10
```

o Scanner produz conceitualmente:

```text
type    = INT
lexeme  = "10"
literal = 10
line    = 1
```

---

## Inteiros e números de ponto flutuante

A Ka diferencia números inteiros e números de ponto flutuante já durante a análise léxica.

```ka
var inteiro = 10;
var decimal = 10.5;
```

O Scanner gera:

```text
10   → INT   → Integer
10.5 → FLOAT → Double
```

Essa é uma extensão em relação à representação genérica de números utilizada na implementação de referência.

---

## Palavras reservadas

Entre as palavras reservadas reconhecidas atualmente estão:

```text
and
case
default
else
false
for
fun
if
nil
or
print
return
switch
this
true
var
while
```

Quando o Scanner encontra uma sequência de caracteres válida para um identificador, ele verifica se ela corresponde a uma dessas palavras.

Por exemplo:

```ka
var
```

é classificado como:

```text
VAR
```

enquanto:

```ka
resultado
```

é classificado como:

```text
IDENTIFIER
```

---

# 2. Parser

Após a análise léxica, a lista de tokens é enviada ao `Parser`.

O Parser realiza a **análise sintática**, verificando se a sequência de tokens corresponde às regras gramaticais da Ka.

A implementação utiliza um **Parser Descendente Recursivo (Recursive Descent Parser)**.

De forma simplificada:

```text
parse
  ↓
declaration
  ↓
statement
```

O Parser reconhece construções como:

- declarações de variáveis;
- funções;
- funções anônimas;
- blocos;
- `if` / `else`;
- `while`;
- `for`;
- `switch` / `case` / `default`;
- `print`;
- `return`;
- expressões;
- chamadas de função;
- objetos e propriedades.

---

# 3. Precedência de operadores

A precedência é definida pela própria estrutura do Parser:

```text
expression
    ↓
assignment
    ↓
or
    ↓
and
    ↓
equality
    ↓
comparison
    ↓
term
    ↓
factor
    ↓
unary
    ↓
call
    ↓
primary
```

Por exemplo:

```ka
2 + 3 * 4
```

é interpretado sintaticamente como:

```text
       +
      / \
     2   *
        / \
       3   4
```

Portanto:

```text
2 + (3 * 4)
```

e não:

```text
(2 + 3) * 4
```

---

# 4. AST — Abstract Syntax Tree

Após reconhecer a estrutura sintática do programa, o Parser constrói uma **Árvore Sintática Abstrata (AST)**.

A AST representa a estrutura lógica do programa sem preservar todos os detalhes da representação textual original.

Os dois grupos principais são:

```text
Expr
Stmt
```

## Expressões (`Expr`)

Atualmente a AST possui expressões como:

```text
Assign
Binary
Call
Function
Get
Grouping
Literal
Logical
ObjectLiteral
Set
This
Unary
Variable
```

## Statements (`Stmt`)

Os statements incluem:

```text
Block
Expression
Function
If
Print
Return
Switch
Var
While
```

---

# 5. Separação entre Token e AST

Uma decisão importante da arquitetura atual é que a AST **não utiliza `Token` diretamente para representar nomes e operadores**.

`Token` pertence à análise léxica.

O Parser funciona como a fronteira entre a representação lexical e a representação sintática:

```text
Scanner
   ↓
 Token
   ↓
 Parser
   ↓
 ┌────────────────┐
 │ Symbol         │
 │ Operator       │
 │ SourceLocation │
 └────────────────┘
   ↓
  AST
```

Isso reduz o acoplamento entre o Scanner e a AST.

---

## Symbol

`Symbol` representa nomes dentro da AST, como:

- variáveis;
- funções;
- parâmetros;
- propriedades;
- `this`.

Ele guarda atualmente:

```text
name
line
```

Por exemplo:

```ka
var resultado = 10;
```

o nome `resultado` pode ser representado na AST como:

```text
Symbol
├── name = "resultado"
└── line = 1
```

O Parser recebe um `Token` do Scanner e extrai apenas as informações necessárias para construir o `Symbol`.

---

## Operator

Operadores utilizados pela AST são representados pela classe `Operator`.

Ela possui um `Operator.Kind` específico para os operadores aceitos em expressões:

```text
PLUS
MINUS
STAR
SLASH

GREATER
GREATER_EQUAL
LESS
LESS_EQUAL

BANG_EQUAL
EQUAL_EQUAL

BANG

AND
OR
```

Essa separação evita utilizar `TokenType` diretamente na AST.

`TokenType` representa todos os tokens da linguagem, incluindo elementos que não são operadores:

```text
VAR
IF
EOF
LEFT_PAREN
SWITCH
...
```

Já `Operator.Kind` representa exclusivamente operadores válidos.

---

## SourceLocation

`SourceLocation` representa uma localização no código-fonte sem exigir que a AST mantenha um `Token`.

Na implementação atual, ela armazena:

```text
line
```

Essa abstração permite que futuramente sejam adicionadas informações como coluna e arquivo sem precisar acoplar novamente a AST ao Scanner.

---

# 6. Objetos

A Ka possui suporte sintático a literais de objeto.

Exemplo:

```ka
var pessoa = {
    nome: "Ana",
    idade: 20
};
```

O Parser produz um:

```text
Expr.ObjectLiteral
```

O acesso a uma propriedade:

```ka
pessoa.nome;
```

é representado por:

```text
Expr.Get
```

Enquanto uma atribuição:

```ka
pessoa.idade = 21;
```

é representada por:

```text
Expr.Set
```

---

# 7. Funções

A Ka reconhece funções nomeadas:

```ka
fun soma(a, b) {
    return a + b;
}
```

e chamadas:

```ka
soma(10, 20);
```

Também são reconhecidas funções anônimas:

```ka
var dobro = fun(x) {
    return x * 2;
};
```

O Parser diferencia uma declaração de função nomeada de uma função utilizada como expressão.

---

# 8. Estruturas de controle

## If / Else

```ka
if (x > 10) {
    print x;
} else {
    print 0;
}
```

## While

```ka
while (x < 10) {
    x = x + 1;
}
```

## For

A sintaxe da Ka reconhece `for`:

```ka
for (var i = 0; i < 3; i = i + 1) {
    print i;
}
```

Porém, não existe um `Stmt.For` na AST.

O Parser realiza **desugaring**, transformando o `for` em construções já existentes.

Conceitualmente:

```ka
{
    var i = 0;

    while (i < 3) {
        print i;
        i = i + 1;
    }
}
```

Assim, a sintaxe oferece `for` sem exigir uma representação exclusiva para ele na AST.

---

# 9. Switch / Case / Default

A versão atual da Ka também adiciona suporte sintático a:

```text
switch
case
default
```

Exemplo:

```ka
switch (x) {
    case 1:
        print "um";

    case 2:
        print "dois";

    default:
        print "outro";
}
```

A AST representa essa construção através de:

```text
Stmt.Switch
```

O nó armazena:

- expressão analisada pelo `switch`;
- valores dos `case`;
- corpo de cada `case`;
- corpo opcional de `default`.

O Parser também detecta mais de um `default` dentro do mesmo `switch`.

---

# 10. Tratamento de erros

O projeto diferencia **erros léxicos** de **erros sintáticos**.

## Erro léxico

Ocorre quando o Scanner encontra uma sequência que não consegue transformar em token válido.

Exemplo:

```ka
@
```

Outro exemplo é uma string sem fechamento:

```ka
print "texto;
```

## Erro sintático

Ocorre quando os tokens existem, mas estão organizados de maneira incompatível com a gramática.

Exemplo:

```ka
var x = ;
```

O Parser também possui um mecanismo de sincronização para tentar continuar a análise depois de determinados erros sintáticos.

---

# 11. AstPrinter

A versão atual inclui `AstPrinter.java`.

Sua função é percorrer a AST e gerar uma representação textual no formato de **S-expression**.

Isso permite visualizar concretamente o resultado produzido pelo Parser.

Por exemplo:

```ka
var x = 10 + 5 * 2;
```

produz:

```text
(var x (+ 10 (* 5 2)))
```

Isso também permite visualizar a precedência.

A expressão:

```ka
10 + 5 * 2
```

aparece como:

```text
(+ 10 (* 5 2))
```

mostrando que a multiplicação foi agrupada antes da soma.

O `AstPrinter` é utilizado para visualização e depuração; ele não executa o programa.

---

# 12. Estrutura do projeto

```text
LinguagemKA-ProjetoCompiladores/
│
├── Ka/
│   ├── ast/
│   │   ├── Expr.java
│   │   └── Stmt.java
│   │
│   ├── AstPrinter.java
│   ├── Ka.java
│   ├── Operator.java
│   ├── Parser.java
│   ├── Scanner.java
│   ├── SourceLocation.java
│   ├── Symbol.java
│   ├── Token.java
│   └── TokenType.java
│
├── tool/
│   └── GenerateAst.java
│
├── teste.ka
└── README.md
```

### Responsabilidade dos principais arquivos

| Arquivo | Responsabilidade |
|---|---|
| `Ka.java` | Ponto de entrada e integração Scanner → Parser → AstPrinter |
| `Scanner.java` | Análise léxica |
| `Token.java` | Representação de um token |
| `TokenType.java` | Tipos de tokens da Ka |
| `Parser.java` | Análise sintática |
| `Expr.java` | Nós de expressão da AST |
| `Stmt.java` | Nós de statements da AST |
| `Symbol.java` | Representação de nomes na AST |
| `Operator.java` | Representação restrita dos operadores da AST |
| `SourceLocation.java` | Informação de localização no código-fonte |
| `AstPrinter.java` | Impressão da AST |
| `GenerateAst.java` | Utilitário para geração das classes da AST |

---

# 13. Como compilar

## Requisitos

É necessário possuir Java e `javac` instalados.

Verifique com:

```bash
java --version
javac --version
```

Na raiz do projeto, compile os arquivos com:

### Linux/macOS

```bash
javac -d out $(find Ka -name "*.java")
```

### Windows PowerShell

```powershell
javac -d out (Get-ChildItem -Recurse Ka -Filter *.java).FullName
```

---

# 14. Como executar

O repositório já possui o arquivo:

```text
teste.ka
```

Depois da compilação:

```bash
java -cp out ka.Ka teste.ka
```

Também é possível informar qualquer outro arquivo `.ka`:

```bash
java -cp out ka.Ka caminho/do/arquivo.ka
```

Sem informar um arquivo, o programa inicia o prompt interativo:

```bash
java -cp out ka.Ka
```

Exemplo:

```text
ka> var x = 10 + 5;
```

---

# 15. Exemplo completo

O arquivo `teste.ka` atual contém:

```ka
var x = 10 + 5 * 2;

if (x > 10) {
    print x;
}
```

Execute:

```bash
java -cp out ka.Ka teste.ka
```

O programa primeiro mostra os tokens reconhecidos:

```text
=== Tokens ===
VAR var null
IDENTIFIER x null
EQUAL = null
INT 10 10
PLUS + null
INT 5 5
STAR * null
INT 2 2
SEMICOLON ; null
...
EOF  null
```

Em seguida, mostra a AST:

```text
=== Parser ===
(var x (+ 10 (* 5 2)))
(if (> x 10) (block (print x)))
```

E, se não houver erros:

```text
Analise sintatica concluida sem erros: 2 declaracao(oes).
```

---

# 16. Exemplo para testar mais funcionalidades

Crie um arquivo chamado `exemplo.ka`:

```ka
var inteiro = 10;
var decimal = 3.14;

var pessoa = {
    nome: "Ka",
    idade: inteiro
};

fun soma(a, b) {
    return a + b;
}

var dobro = fun(x) {
    return x * 2;
};

if (inteiro > 5) {
    print pessoa.nome;
}

switch (inteiro) {
    case 5:
        print "cinco";

    case 10:
        print "dez";

    default:
        print "outro";
}

for (var i = 0; i < 3; i = i + 1) {
    print i;
}
```

Execute:

```bash
java -cp out ka.Ka exemplo.ka
```

O programa deverá realizar a análise léxica, construir a AST e imprimi-la.

---

# 17. Escopo atual

Esta versão implementa:

```text
Código-fonte
      ↓
Análise léxica
      ↓
Tokens
      ↓
Análise sintática
      ↓
AST
      ↓
Visualização da AST
```

O objetivo desta etapa é reconhecer e representar corretamente a estrutura do programa.

O `AstPrinter` permite inspecionar essa estrutura, mas **não corresponde à execução da linguagem**.

---

# Integrantes

| Nome completo | Login institucional |
|---|---|
| Camila Xavier de Medeiros | cxm |
| Juan | jlcm |
| Mariana | mms-11 |
| Rinaldo | rsbj |
| Vinícius | |

---

# Referência

A implementação utiliza como referência conceitual e arquitetural:

**Robert Nystrom — *Crafting Interpreters***

A linguagem apresentada no livro é a **Lox**.

A Ka utiliza conceitos dessa implementação como Scanner, Parser descendente recursivo, AST e padrão Visitor, mas estende e adapta essa base para as decisões do projeto.

Entre as adaptações presentes na versão atual estão:

- distinção léxica entre `INT` e `FLOAT`;
- suporte a `switch`, `case` e `default`;
- funções anônimas;
- literais de objeto e acesso a propriedades;
- estruturas próprias da AST como `Symbol`, `Operator` e `SourceLocation`;
- impressão da AST através de `AstPrinter`.
