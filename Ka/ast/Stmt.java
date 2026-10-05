package ka.ast;

import java.util.List;

import ka.Operator;
import ka.SourceLocation;
import ka.Symbol;

public abstract class Stmt {
  public interface Visitor<R> {
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

  public static class Block extends Stmt {
    public Block(List<Stmt> statements) {
      this.statements = statements;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockStmt(this);
    }

    public final List<Stmt> statements;
  }

  public static class Expression extends Stmt {
    public Expression(Expr expression) {
      this.expression = expression;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

    public final Expr expression;
  }

  public static class Function extends Stmt {
    public Function(Symbol name, List<Symbol> params, List<Stmt> body) {
      this.name = name;
      this.params = params;
      this.body = body;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionStmt(this);
    }

    public final Symbol name;
    public final List<Symbol> params;
    public final List<Stmt> body;
  }

  public static class If extends Stmt {
    public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfStmt(this);
    }

    public final Expr condition;
    public final Stmt thenBranch;
    public final Stmt elseBranch;
  }

  public static class Print extends Stmt {
    public Print(Expr expression) {
      this.expression = expression;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitPrintStmt(this);
    }

    public final Expr expression;
  }

  public static class Return extends Stmt {
    public Return(SourceLocation location, Expr value) {
      this.location = location;
      this.value = value;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnStmt(this);
    }

    public final SourceLocation location;
    public final Expr value;
  }

  public static class Switch extends Stmt {
    public Switch(Expr subject, List<Expr> caseValues, List<List<Stmt>> caseBodies, List<Stmt> defaultBranch) {
      this.subject = subject;
      this.caseValues = caseValues;
      this.caseBodies = caseBodies;
      this.defaultBranch = defaultBranch;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSwitchStmt(this);
    }

    public final Expr subject;
    public final List<Expr> caseValues;
    public final List<List<Stmt>> caseBodies;
    public final List<Stmt> defaultBranch;
  }

  public static class Var extends Stmt {
    public Var(Symbol name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitVarStmt(this);
    }

    public final Symbol name;
    public final Expr initializer;
  }

  public static class While extends Stmt {
    public While(Expr condition, Stmt body) {
      this.condition = condition;
      this.body = body;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitWhileStmt(this);
    }

    public final Expr condition;
    public final Stmt body;
  }

  public abstract <R> R accept(Visitor<R> visitor);
}
