package ka;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ka.ast.Expr;
import ka.ast.FieldPattern;
import ka.ast.MatchCase;
import ka.ast.Pattern;
import ka.ast.Stmt;

import static ka.TokenType.*;

class Parser {
    // Excecao usada so para "pular" ate o proximo ponto de sincronizacao
    // quando acha um erro sintatico. Nao e mostrada ao usuario.
    private static class ParseError extends RuntimeException {}

    private static final int MAX_ARGUMENTS = 255;

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Ponto de entrada: program -> declaration* EOF
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            addIfNotNull(statements, declaration());
        }
        return statements;
    }

    // ---------------------------------------------------------------
    // Declaracoes
    // ---------------------------------------------------------------

    private Stmt declaration() {
        try {
            // "fun nome(...)" e declaracao. "fun (...)" no inicio de um
            // statement e uma funcao anonima (expressao), entao cai em
            // statement() -> expressionStatement().
            if (check(FUN) && checkNext(IDENTIFIER)) {
                advance();
                return function();
            }
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // funDecl -> "fun" IDENTIFIER "(" parameters? ")" block
    // (o "fun" ja foi consumido por declaration())
    private Stmt.Function function() {
        Token name = consume(IDENTIFIER, "Esperado o nome da funcao.");
        consume(LEFT_PAREN, "Esperado '(' depois do nome da funcao.");
        List<Symbol> parameters = parameters();
        consume(LEFT_BRACE, "Esperado '{' antes do corpo da funcao.");
        List<Stmt> body = block();
        return new Stmt.Function(new Symbol(name), parameters, body);
    }

    // parameters? ")"  -- o "(" ja foi consumido; consome o ")".
    private List<Symbol> parameters() {
        List<Symbol> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= MAX_ARGUMENTS) {
                    error(peek(), "Nao pode ter mais de " + MAX_ARGUMENTS + " parametros.");
                }
                parameters.add(new Symbol(consume(IDENTIFIER, "Esperado o nome do parametro.")));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Esperado ')' depois dos parametros.");
        return parameters;
    }

    // varDecl -> "var" IDENTIFIER ( "=" expression )? ";"
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Esperado o nome da variavel.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Esperado ';' depois da declaracao da variavel.");
        return new Stmt.Var(new Symbol(name), initializer);
    }

    // ---------------------------------------------------------------
    // Statements
    // ---------------------------------------------------------------

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(SWITCH)) return switchStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    // O "for" nao existe na AST: vira um While dentro de um Block.
    //
    //   for (init; cond; inc) body
    //     =>
    //   { init; while (cond) { body; inc; } }
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Esperado '(' depois de 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Esperado ';' depois da condicao do for.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Esperado ')' depois das clausulas do for.");

        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Esperado '(' depois de 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Esperado ')' depois da condicao do if.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        // O "else" sempre pertence ao "if" mais proximo.
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Esperado ';' depois do valor.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Esperado ';' depois do valor do return.");
        return new Stmt.Return(new SourceLocation(keyword), value);
    }

    // switchStmt -> "switch" "(" expression ")" "{" clause* "}"
    //
    // Na AST: caseValues[i] e caseBodies[i] andam em paralelo.
    // defaultBranch e null se nao houver "default" (e uma lista vazia se
    // houver "default:" sem comandos).
    private Stmt switchStatement() {
        consume(LEFT_PAREN, "Esperado '(' depois de 'switch'.");
        Expr subject = expression();
        consume(RIGHT_PAREN, "Esperado ')' depois do valor do switch.");
        consume(LEFT_BRACE, "Esperado '{' antes dos cases do switch.");

        List<Expr> caseValues = new ArrayList<>();
        List<List<Stmt>> caseBodies = new ArrayList<>();
        List<Stmt> defaultBranch = null;

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            try {
                if (match(CASE)) {
                    Expr value = expression();
                    consume(COLON, "Esperado ':' depois do valor do case.");
                    caseValues.add(value);
                    caseBodies.add(caseBody());
                } else if (match(DEFAULT)) {
                    Token keyword = previous();
                    consume(COLON, "Esperado ':' depois de 'default'.");
                    List<Stmt> body = caseBody();
                    if (defaultBranch != null) {
                        error(keyword, "Mais de um 'default' no switch.");
                    } else {
                        defaultBranch = body;
                    }
                } else {
                    throw error(peek(), "Esperado 'case' ou 'default' dentro do switch.");
                }
            } catch (ParseError error) {
                // Erro no cabecalho de um case: pula ate o proximo case/default
                // (ou o "}" do switch) sem derrubar o resto da analise.
                while (!isAtEnd() && !check(CASE) && !check(DEFAULT) && !check(RIGHT_BRACE)) {
                    advance();
                }
            }
        }

        consume(RIGHT_BRACE, "Esperado '}' depois dos cases do switch.");
        return new Stmt.Switch(subject, caseValues, caseBodies, defaultBranch);
    }

    // Comandos de um case/default: ate o proximo case, default ou "}".
    private List<Stmt> caseBody() {
        List<Stmt> body = new ArrayList<>();
        while (!check(CASE) && !check(DEFAULT) && !check(RIGHT_BRACE) && !isAtEnd()) {
            addIfNotNull(body, declaration());
        }
        return body;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Esperado '(' depois de 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Esperado ')' depois da condicao do while.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Esperado ';' depois da expressao.");
        return new Stmt.Expression(expr);
    }

    // block -> "{" declaration* "}"   (o "{" ja foi consumido)
    // Devolve so a lista; quem chama decide se embrulha num Stmt.Block
    // (bloco comum) ou usa direto (corpo de funcao).
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            addIfNotNull(statements, declaration());
        }

        consume(RIGHT_BRACE, "Esperado '}' depois do bloco.");
        return statements;
    }

    // ---------------------------------------------------------------
    // Expressoes (uma funcao por nivel de precedencia)
    // ---------------------------------------------------------------

    private Expr expression() {
        return assignment();
    }

    // assignment -> ( call "." )? IDENTIFIER "=" assignment | logic_or
    //
    // Estrategia: le o lado esquerdo como expressao comum; so se aparecer
    // um "=" depois e que descobrimos que era um alvo de atribuicao.
    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();   // associativa a direita: a = b = c

            if (expr instanceof Expr.Variable) {
                Symbol name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get) expr;
                return new Expr.Set(get.object, get.name, value);
            }

            // Nao lanca: o parser nao esta "perdido", so o alvo e invalido.
            error(equals, "Alvo de atribuicao invalido.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Operator operator = new Operator(previous());
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Operator operator = new Operator(previous());
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Operator operator = new Operator(previous());
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Operator operator = new Operator(previous());
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Operator operator = new Operator(previous());
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Operator operator = new Operator(previous());
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Operator operator = new Operator(previous());
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // call -> primary ( "(" arguments? ")" | "." IDENTIFIER )*
    // Aceita encadeamento: a.b(1).c.d(2)
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Esperado o nome da propriedade depois de '.'.");
                expr = new Expr.Get(expr, new Symbol(name));
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= MAX_ARGUMENTS) {
                    error(peek(), "Nao pode ter mais de " + MAX_ARGUMENTS + " argumentos.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Esperado ')' depois dos argumentos.");

        // A localizacao guardada e a do ")" - e onde um erro de execucao da
        // chamada (ex: aridade errada) deve apontar.
        return new Expr.Call(callee, new SourceLocation(paren), arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        // INT -> Integer, FLOAT -> Double, STRING -> String (ja convertidos
        // pelo Scanner e guardados em token.literal).
        if (match(INT, FLOAT, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(THIS)) return new Expr.This(new Symbol(previous()));

        if (match(IDENTIFIER)) {
            return new Expr.Variable(new Symbol(previous()));
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Esperado ')' depois da expressao.");
            return new Expr.Grouping(expr);
        }

        // Funcao anonima: fun (a, b) { ... }
        if (match(FUN)) {
            consume(LEFT_PAREN, "Esperado '(' depois de 'fun'.");
            List<Symbol> parameters = parameters();
            consume(LEFT_BRACE, "Esperado '{' antes do corpo da funcao.");
            List<Stmt> body = block();
            return new Expr.Function(parameters, body);
        }

        if (match(MATCH)) return matchExpression();

        // Em posicao de expressao, "{" so pode ser um literal de objeto.
        if (match(LEFT_BRACE)) return objectLiteral();

        throw error(peek(), "Esperada uma expressao.");
    }

    // objectLiteral -> "{" ( IDENTIFIER ":" expression ( "," ... )* )? "}"
    // (o "{" ja foi consumido). Nao aceita virgula sobrando: { x: 1, }
    private Expr objectLiteral() {
        List<Symbol> keys = new ArrayList<>();
        List<Expr> values = new ArrayList<>();

        if (!check(RIGHT_BRACE)) {
            do {
                Token key = consume(IDENTIFIER, "Esperado o nome da propriedade.");
                consume(COLON, "Esperado ':' depois do nome da propriedade.");
                keys.add(new Symbol(key));
                values.add(expression());
            } while (match(COMMA));
        }

        consume(RIGHT_BRACE, "Esperado '}' depois das propriedades do objeto.");
        return new Expr.ObjectLiteral(keys, values);
    }

    // ---------------------------------------------------------------
    // Pattern matching
    // ---------------------------------------------------------------

    // matchExpr -> "match" "(" expression ")" "{" ( "case" pattern "=>" expression ";" )* "}"
    // (o "match" ja foi consumido por primary())
    private Expr matchExpression() {
        SourceLocation location = new SourceLocation(previous());   // a palavra "match"
        consume(LEFT_PAREN, "Esperado '(' depois de 'match'.");
        Expr scrutinee = expression();
        consume(RIGHT_PAREN, "Esperado ')' depois do valor do match.");
        consume(LEFT_BRACE, "Esperado '{' antes dos cases do match.");

        List<MatchCase> cases = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            consume(CASE, "Esperado 'case' dentro do match.");
            Pattern pattern = pattern();
            consume(ARROW, "Esperado '=>' depois do padrao.");
            Expr body = expression();
            consume(SEMICOLON, "Esperado ';' depois do corpo do case.");
            cases.add(new MatchCase(pattern, body));
        }

        Token close = consume(RIGHT_BRACE, "Esperado '}' depois dos cases do match.");
        if (cases.isEmpty()) {
            error(close, "O match precisa de pelo menos um 'case'.");
        }
        return new Expr.Match(location, scrutinee, cases);
    }

    // Nomes de tipo aceitos em padroes como Number(n).
    private static final List<String> TYPE_NAMES = Arrays.asList(
        "Number", "Int", "Float", "String", "Boolean", "Nil", "Object", "Function");

    // pattern -> "_"
    //          | "nil" | "true" | "false" | INT | FLOAT | STRING | "-" (INT | FLOAT)
    //          | IDENTIFIER ( "(" ( IDENTIFIER | "_" )? ")" )?     -- tipo, com binding opcional
    //          | "{" ( fieldPattern ( "," fieldPattern )* )? "}"   -- objeto
    private Pattern pattern() {
        if (match(UNDERSCORE)) return new Pattern.Wildcard();

        if (match(NIL)) return new Pattern.Literal(null);
        if (match(TRUE)) return new Pattern.Literal(true);
        if (match(FALSE)) return new Pattern.Literal(false);
        if (match(INT, FLOAT, STRING)) return new Pattern.Literal(previous().literal);

        // Numero negativo: -1, -2.5
        if (check(MINUS) && (checkNext(INT) || checkNext(FLOAT))) {
            advance();
            Object number = advance().literal;
            if (number instanceof Integer) return new Pattern.Literal(-(Integer) number);
            return new Pattern.Literal(-(Double) number);
        }

        if (match(IDENTIFIER)) {
            Token type = previous();
            if (!TYPE_NAMES.contains(type.lexeme)) {
                error(type, "Tipo desconhecido '" + type.lexeme + "' no padrao.");
            }
            String binding = null;
            SourceLocation location = new SourceLocation(type);
            if (match(LEFT_PAREN)) {
                if (match(IDENTIFIER)) {
                    binding = previous().lexeme;
                    location = new SourceLocation(previous());
                } else {
                    match(UNDERSCORE);   // Number(_): sem binding
                }
                consume(RIGHT_PAREN, "Esperado ')' depois do nome do binding.");
            }
            return new Pattern.TypePattern(type.lexeme, binding, location);
        }

        if (match(LEFT_BRACE)) return objectPattern();

        throw error(peek(), "Esperado um padrao.");
    }

    // fieldPattern -> IDENTIFIER                -- atalho: { nome }  = { nome: nome }
    //               | IDENTIFIER ":" IDENTIFIER -- binding:  { nome: n }
    //               | IDENTIFIER ":" pattern    -- subpadrao: { idade: 24 }, { x: Number(v) }
    // (o "{" ja foi consumido)
    private Pattern objectPattern() {
        List<FieldPattern> fields = new ArrayList<>();

        if (!check(RIGHT_BRACE)) {
            do {
                Token name = consume(IDENTIFIER, "Esperado o nome do campo no padrao.");
                if (match(COLON)) {
                    // IDENTIFIER sozinho e binding; IDENTIFIER seguido de "(" e padrao de tipo.
                    if (check(IDENTIFIER) && !checkNext(LEFT_PAREN)) {
                        fields.add(new FieldPattern(name.lexeme, null, advance().lexeme, new SourceLocation(name)));
                    } else {
                        fields.add(new FieldPattern(name.lexeme, pattern(), null, new SourceLocation(name)));
                    }
                } else {
                    fields.add(new FieldPattern(name.lexeme, null, name.lexeme, new SourceLocation(name)));
                }
            } while (match(COMMA));
        }

        consume(RIGHT_BRACE, "Esperado '}' depois dos campos do padrao.");
        return new Pattern.ObjectPattern(fields);
    }

    // ---------------------------------------------------------------
    // Ferramentas de navegacao pelos tokens
    // ---------------------------------------------------------------

    // Se o token atual for de algum dos tipos, consome e devolve true.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // Exige um token do tipo dado: consome e devolve, ou reporta o erro.
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // Olha o token DEPOIS do atual, sem consumir nada.
    private boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type == EOF) return false;
        return tokens.get(current + 1).type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private void addIfNotNull(List<Stmt> list, Stmt stmt) {
        if (stmt != null) list.add(stmt);
    }

    // Reporta o erro e devolve a excecao. Quem quer abortar a regra atual
    // faz "throw error(...)"; quem so quer registrar o erro e seguir
    // (ex: alvo de atribuicao invalido) chama "error(...)" sem lancar.
    private ParseError error(Token token, String message) {
        Ka.error(token, message);
        return new ParseError();
    }

    // Modo panico: depois de um erro, descarta tokens ate achar um ponto
    // onde provavelmente comeca um novo statement, para continuar a
    // analise e achar mais erros de uma vez so.
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                case SWITCH:
                case CASE:
                case DEFAULT:
                    return;
                default:
                    break;
            }

            advance();
        }
    }
}
