package ka;

import java.util.List;

import ka.ast.Expr;
import ka.ast.Stmt;

/*
 * Imprime a AST em forma de S-expression, uma linha por declaracao.
 * So serve para depurar/mostrar o resultado do Parser; nada depende dele.
 *
 *   var x = 10 + 5 * 2;   =>   (var x (+ 10 (* 5 2)))
 */
class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

    String print(List<Stmt> statements) {
        StringBuilder out = new StringBuilder();
        for (Stmt statement : statements) {
            out.append(statement.accept(this)).append("\n");
        }
        return out.toString();
    }

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        return parenthesize("block", stmt.statements);
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return parenthesize("expr", stmt.expression);
    }

    @Override
    public String visitFunctionStmt(Stmt.Function stmt) {
        return "(fun " + stmt.name + " " + params(stmt.params) + body(stmt.body) + ")";
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        if (stmt.elseBranch == null) {
            return parenthesizeWithStmts("if", stmt.condition, stmt.thenBranch);
        }
        return parenthesizeWithStmts("if-else", stmt.condition, stmt.thenBranch, stmt.elseBranch);
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        return parenthesize("print", stmt.expression);
    }

    @Override
    public String visitReturnStmt(Stmt.Return stmt) {
        if (stmt.value == null) return "(return)";
        return parenthesize("return", stmt.value);
    }

    @Override
    public String visitSwitchStmt(Stmt.Switch stmt) {
        StringBuilder builder = new StringBuilder("(switch ");
        builder.append(stmt.subject.accept(this));
        for (int i = 0; i < stmt.caseValues.size(); i++) {
            builder.append(" (case ").append(stmt.caseValues.get(i).accept(this))
                   .append(body(stmt.caseBodies.get(i))).append(")");
        }
        if (stmt.defaultBranch != null) {
            builder.append(" (default").append(body(stmt.defaultBranch)).append(")");
        }
        return builder.append(")").toString();
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer == null) return "(var " + stmt.name + ")";
        return "(var " + stmt.name + " " + stmt.initializer.accept(this) + ")";
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        return parenthesizeWithStmts("while", stmt.condition, stmt.body);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return "(= " + expr.name + " " + expr.value.accept(this) + ")";
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme(), expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        StringBuilder builder = new StringBuilder("(call ");
        builder.append(expr.callee.accept(this));
        for (Expr argument : expr.arguments) {
            builder.append(" ").append(argument.accept(this));
        }
        return builder.append(")").toString();
    }

    @Override
    public String visitFunctionExpr(Expr.Function expr) {
        return "(fun " + params(expr.params) + body(expr.body) + ")";
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return "(. " + expr.object.accept(this) + " " + expr.name + ")";
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        if (expr.value instanceof String) return "\"" + expr.value + "\"";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme(), expr.left, expr.right);
    }

    @Override
    public String visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
        StringBuilder builder = new StringBuilder("(object");
        for (int i = 0; i < expr.keys.size(); i++) {
            builder.append(" ").append(expr.keys.get(i)).append(":")
                   .append(expr.values.get(i).accept(this));
        }
        return builder.append(")").toString();
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return "(.= " + expr.object.accept(this) + " " + expr.name + " "
            + expr.value.accept(this) + ")";
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return "this";
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme(), expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.toString();
    }

    // ---------------- auxiliares ----------------

    private String params(List<Symbol> params) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) builder.append(" ");
            builder.append(params.get(i));
        }
        return builder.append(")").toString();
    }

    // " stmt1 stmt2 ..." (com espaco na frente, ou vazio se nao ha comandos)
    private String body(List<Stmt> statements) {
        StringBuilder builder = new StringBuilder();
        for (Stmt statement : statements) {
            builder.append(" ").append(statement.accept(this));
        }
        return builder.toString();
    }

    private String parenthesize(String name, List<Stmt> statements) {
        return "(" + name + body(statements) + ")";
    }

    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ").append(expr.accept(this));
        }
        return builder.append(")").toString();
    }

    private String parenthesizeWithStmts(String name, Expr expr, Stmt... stmts) {
        StringBuilder builder = new StringBuilder("(").append(name)
            .append(" ").append(expr.accept(this));
        for (Stmt stmt : stmts) {
            builder.append(" ").append(stmt.accept(this));
        }
        return builder.append(")").toString();
    }
}
