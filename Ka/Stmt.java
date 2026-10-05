package com.craftinginterpreters.ka;

import java.util.List;

abstract class Stmt {
  interface Visitor<R> {
    R visitBlockStmt(Block stmt);
    R visitExpressionStmt(Expression stmt);
    R visitFunctionStmt(Function stmt);
    R visitIfStmt(If stmt);
    R visitPrintStmt(Print stmt);
    R visitReturnStmt(Return stmt);
    R visitSwitchStmt(Switch stmt);
    R visitVarStmt(Var stmt);
    R visitWhileStmt(While stmt);
  }

  static class Block extends Stmt {
    Block(List<Stmt> statements) {
      this.statements = statements;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockStmt(this);
    }

    final List<Stmt> statements;
  }

  static class Expression extends Stmt {
    Expression(Expr expression) {
      this.expression = expression;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

    final Expr expression;
  }

  static class Function extends Stmt {
    Function(Symbol name, List<Symbol> params, List<Stmt> body) {
      this.name = name;
      this.params = params;
      this.body = body;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionStmt(this);
    }

    final Symbol name;
    final List<Symbol> params;
    final List<Stmt> body;
  }

  static class If extends Stmt {
    If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfStmt(this);
    }

    final Expr condition;
    final Stmt thenBranch;
    final Stmt elseBranch;
  }

  static class Print extends Stmt {
    Print(Expr expression) {
      this.expression = expression;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitPrintStmt(this);
    }

    final Expr expression;
  }

  static class Return extends Stmt {
    Return(SourceLocation location, Expr value) {
      this.location = location;
      this.value = value;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnStmt(this);
    }

    final SourceLocation location;
    final Expr value;
  }

  static class Switch extends Stmt {
    Switch(Expr subject, List<Expr> caseValues, List<List<Stmt>> caseBodies, List<Stmt> defaultBranch) {
      this.subject = subject;
      this.caseValues = caseValues;
      this.caseBodies = caseBodies;
      this.defaultBranch = defaultBranch;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitSwitchStmt(this);
    }

    final Expr subject;
    final List<Expr> caseValues;
    final List<List<Stmt>> caseBodies;
    final List<Stmt> defaultBranch;
  }

  static class Var extends Stmt {
    Var(Symbol name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitVarStmt(this);
    }

    final Symbol name;
    final Expr initializer;
  }

  static class While extends Stmt {
    While(Expr condition, Stmt body) {
      this.condition = condition;
      this.body = body;
    }

    <R> R accept(Visitor<R> visitor) {
      return visitor.visitWhileStmt(this);
    }

    final Expr condition;
    final Stmt body;
  }

  abstract <R> R accept(Visitor<R> visitor);
}
