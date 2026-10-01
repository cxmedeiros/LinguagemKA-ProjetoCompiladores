# Ka Language — Scanner e Parser

Este projeto implementa as etapas iniciais de uma linguagem de programação chamada **Ka**, desenvolvida em Java como parte do estudo de Compiladores.

Nesta versão, o projeto contempla a **análise léxica (Scanner/Lexer)** e a **análise sintática (Parser)**, incluindo a construção da **Árvore Sintática Abstrata (AST)**.

## Fluxo do projeto

O processamento realizado nesta etapa pode ser representado por:

```text
Código-fonte
     ↓
Scanner / Lexer
     ↓
Tokens
     ↓
Parser
     ↓
AST
```

O objetivo, neste momento, não é executar o programa escrito em Ka, mas analisar sua estrutura léxica e sintática.

---

## 1. Scanner / Lexer

O `Scanner` é responsável pela **análise léxica**.

Ele percorre o código-fonte caractere por caractere e identifica os diferentes elementos da linguagem, transformando-os em **tokens**.

Por exemplo:

```ka
var x = 10 + 5;
```

pode gerar uma sequência de tokens equivalente a:

```text
VAR
IDENTIFIER(x)
EQUAL
NUMBER(10)
PLUS
NUMBER(5)
SEMICOLON
EOF
```

Entre os elementos reconhecidos pelo Scanner estão:

- palavras-chave;
- identificadores;
- números;
- strings;
- operadores;
- símbolos;
- delimitadores.

Os tokens são representados principalmente pelas classes:

```text
Token.java
TokenType.java
```

---

## 2. Parser

Após a análise léxica, os tokens são enviados para o `Parser`.

O Parser é responsável pela **análise sintática**, verificando se a sequência de tokens corresponde às regras gramaticais da linguagem e construindo uma representação estruturada do código.

O projeto utiliza um **Parser Descendente Recursivo (Recursive Descent Parser)**.

Nesse tipo de parser, diferentes regras da gramática são implementadas através de métodos Java.

Por exemplo:

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

Essa organização também define a precedência dos operadores.

Por exemplo:

```ka
2 + 3 * 4
```

é representado estruturalmente como:

```text
      +
     / \
    2   *
       / \
      3   4
```

Assim, a multiplicação possui precedência maior que a soma.

---

## 3. AST — Abstract Syntax Tree

O resultado da análise sintática é representado através de uma **Árvore Sintática Abstrata (AST)**.

A AST representa a estrutura lógica do programa sem manter todos os detalhes presentes no código-fonte original.

Os principais tipos de nós são:

```text
Expr
Stmt
```

`Expr` representa expressões, como:

```ka
10 + 20
x > 5
a * b
```

Enquanto `Stmt` representa instruções, como:

```ka
var x = 10;
print x;
```

ou estruturas como:

```ka
if (x > 5) {
    print x;
}
```

---

## Estrutura do projeto

Os principais arquivos utilizados até esta etapa são:

```text
src/
└── com/
    └── craftinginterpreters/
        └── lox/
            ├── Ka.java
            ├── Scanner.java
            ├── Parser.java
            ├── Expr.java
            ├── Stmt.java
            ├── Token.java
            └── TokenType.java
```

### Responsabilidade dos arquivos

```text
Ka.java
    ↓
Ponto de entrada da aplicação.

Scanner.java
    ↓
Realiza a análise léxica do código-fonte.

Token.java
    ↓
Representa individualmente cada token encontrado.

TokenType.java
    ↓
Define os tipos de tokens existentes na linguagem.

Parser.java
    ↓
Realiza a análise sintática dos tokens.

Expr.java
Stmt.java
    ↓
Definem os nós utilizados para representar a AST.
```

---

## Como executar

### Requisitos

É necessário possuir o Java instalado.

Verifique com:

```bash
java --version
javac --version
```

Recomenda-se Java 11 ou superior.

### Compilação

Clone o repositório:

```bash
git clone <URL-DO-REPOSITORIO>
cd Ka-language
```

Compile os arquivos:

```bash
javac -d out src/com/craftinginterpreters/lox/*.java
```

Execute o programa:

```bash
java -cp out com.craftinginterpreters.lox.Ka
```

---

## Exemplo para teste

Um exemplo simples de entrada é:

```ka
var x = 10 + 5 * 2;

if (x > 10) {
    print x;
}
```

### Etapa 1 — Scanner

O Scanner identifica tokens equivalentes a:

```text
VAR
IDENTIFIER
EQUAL
NUMBER
PLUS
NUMBER
STAR
NUMBER
SEMICOLON

IF
LEFT_PAREN
IDENTIFIER
GREATER
NUMBER
RIGHT_PAREN
LEFT_BRACE
PRINT
IDENTIFIER
SEMICOLON
RIGHT_BRACE

EOF
```

### Etapa 2 — Parser

O Parser utiliza esses tokens para reconhecer a estrutura do programa.

De forma simplificada:

```text
Programa
│
├── Declaração de variável
│   │
│   ├── nome: x
│   │
│   └── valor
│       └── +
│           ├── 10
│           └── *
│               ├── 5
│               └── 2
│
└── If
    │
    ├── condição
    │   └── >
    │       ├── x
    │       └── 10
    │
    └── corpo
        └── print x
```

Esse exemplo também demonstra que o Parser respeita a precedência dos operadores, reconhecendo:

```text
10 + (5 * 2)
```

em vez de:

```text
(10 + 5) * 2
```

---

## Exemplo de erro sintático

O Parser também é responsável por identificar estruturas que não seguem a gramática.

Por exemplo:

```ka
var x = 10
print x;
```

A declaração da variável não possui `;`.

Ao analisar os tokens, o Parser identifica que era esperado um `SEMICOLON` depois da expressão.

O projeto possui ainda um mecanismo de **sincronização após erros sintáticos**, permitindo que o Parser tente continuar a análise do restante do código.

## Integrantes

| Nome completo                   | Login institucional |
|---------------------------------|----------------------|
| Camila Xavier de Medeiros       | cxm                  |
| Juan                            |                      |
| Mariana                         |                      |
| Rinaldo                         |                      |
| Vinícius                        |                      |


