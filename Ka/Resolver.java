package ka;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ka.ast.Expr;
import ka.ast.FieldPattern;
import ka.ast.MatchCase;
import ka.ast.Pattern;
import ka.ast.Stmt;

/*
 * Analise semantica de escopo, executada entre o Parser e o Interpreter.
 *
 * Duas responsabilidades:
 *  1) Calcular, para cada Variable/Assign/This local, a DISTANCIA (numero de
 *     escopos a subir) ate onde o nome foi declarado. Quem nao e resolvido
 *     aqui e global. O resultado fica em locals() para o Interpreter.
 *  2) Reportar erros estaticos: return fora de funcao, 'this' fora de
 *     funcao, leitura de variavel no proprio inicializador e nomes
 *     repetidos no mesmo escopo.
 *
 * CONTRATO COM O INTERPRETER (os escopos criados aqui precisam ser os mesmos
 * em tempo de execucao, senao as distancias ficam erradas):
 *   - Block: um Environment novo.
 *   - Funcao (Stmt.Function / Expr.Function): UM Environment por chamada
 *     contendo "this" (nil se a funcao nao foi vinculada a um objeto) e os
 *     parametros; o corpo executa direto nele, sem Block extra.
 *   - Switch: cada case e o default executam em um Environment proprio.
 *   - Match: cada case executa em um Environment proprio contendo os
 *     bindings do padrao.
 * Break/continue nao existem na Ka, entao nao ha o que verificar.
 */
class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>, Pattern.Visitor<Void> {

    private enum FunctionType { NONE, FUNCTION }

    // Pilha de escopos locais. false = declarado mas ainda nao inicializado.
    private final Deque<Map<String, Boolean>> scopes = new ArrayDeque<>();
    private final Map<Expr, Integer> locals = new IdentityHashMap<>();
    private FunctionType currentFunction = FunctionType.NONE;

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    // Distancia de cada expressao local; expressoes ausentes sao globais.
    Map<Expr, Integer> locals() {
        return locals;
    }

    // ----- Statements -----

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // Define o nome antes do corpo para permitir recursao.
        declare(stmt.name);
        define(stmt.name.name);
        resolveFunction(stmt.params, stmt.body);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Ka.semanticError(stmt.location, "Nao e possivel usar 'return' fora de uma funcao.");
        }
        if (stmt.value != null) resolve(stmt.value);
        return null;
    }

    @Override
    public Void visitSwitchStmt(Stmt.Switch stmt) {
        resolve(stmt.subject);
        for (Expr caseValue : stmt.caseValues) {
            resolve(caseValue);
        }
        for (List<Stmt> body : stmt.caseBodies) {
            resolveScoped(body);
        }
        if (stmt.defaultBranch != null) resolveScoped(stmt.defaultBranch);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // declare/define separados: o inicializador nao pode ler a propria variavel.
        declare(stmt.name);
        if (stmt.initializer != null) resolve(stmt.initializer);
        define(stmt.name.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    // ----- Expressions -----

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr.params, expr.body);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitMatchExpr(Expr.Match expr) {
        resolve(expr.scrutinee);
        for (MatchCase matchCase : expr.cases) {
            beginScope();
            resolve(matchCase.pattern);
            resolve(matchCase.body);
            endScope();
        }
        return null;
    }

    @Override
    public Void visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
        for (Expr value : expr.values) {
            resolve(value);
        }
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentFunction == FunctionType.NONE) {
            Ka.semanticError(expr.keyword.location, "Nao e possivel usar 'this' fora de uma funcao.");
            return null;
        }
        resolveLocal(expr, "this");
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.name) == Boolean.FALSE) {
            Ka.semanticError(expr.name.location,
                "Nao e possivel ler a variavel local '" + expr.name.name
                + "' no proprio inicializador.");
        }
        resolveLocal(expr, expr.name.name);
        return null;
    }

    // ----- Patterns: cada binding e declarado ja inicializado no escopo do case -----

    @Override
    public Void visitLiteralPattern(Pattern.Literal pattern) {
        return null;
    }

    @Override
    public Void visitWildcardPattern(Pattern.Wildcard pattern) {
        return null;
    }

    @Override
    public Void visitTypePatternPattern(Pattern.TypePattern pattern) {
        declareBinding(pattern.binding);
        return null;
    }

    @Override
    public Void visitObjectPatternPattern(Pattern.ObjectPattern pattern) {
        Set<String> fieldNames = new HashSet<>();
        for (FieldPattern field : pattern.fields) {
            if (!fieldNames.add(field.name)) {
                Ka.semanticError(null, "Campo '" + field.name + "' repetido no padrao de objeto.");
            }
            if (field.subPattern != null) resolve(field.subPattern);
            declareBinding(field.binding);
        }
        return null;
    }

    // ----- Auxiliares -----

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolve(Pattern pattern) {
        pattern.accept(this);
    }

    private void resolveScoped(List<Stmt> statements) {
        beginScope();
        resolve(statements);
        endScope();
    }

    private void resolveFunction(List<Symbol> params, List<Stmt> body) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = FunctionType.FUNCTION;

        beginScope();
        scopes.peek().put("this", true);
        for (Symbol param : params) {
            declare(param);
            define(param.name);
        }
        resolve(body);
        endScope();

        currentFunction = enclosingFunction;
    }

    private void resolveLocal(Expr expr, String name) {
        int distance = 0;
        for (Map<String, Boolean> scope : scopes) {
            if (scope.containsKey(name)) {
                locals.put(expr, distance);
                return;
            }
            distance++;
        }
        // Nao achou em nenhum escopo local: e global.
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(Symbol name) {
        if (scopes.isEmpty()) return;
        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.name)) {
            Ka.semanticError(name.location,
                "Ja existe uma variavel chamada '" + name.name + "' neste escopo.");
        }
        scope.put(name.name, false);
    }

    private void define(String name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name, true);
    }

    // Bindings de padrao nao tem localizacao propria (a AST guarda so a String).
    private void declareBinding(String binding) {
        if (binding == null) return;
        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(binding)) {
            Ka.semanticError(null, "Binding '" + binding + "' repetido no mesmo padrao.");
        }
        scope.put(binding, true);
    }
}
