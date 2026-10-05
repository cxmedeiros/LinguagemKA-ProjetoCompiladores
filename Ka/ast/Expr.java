package ka.ast;

import java.util.List;

import ka.Operator;
import ka.SourceLocation;
import ka.Symbol;

public abstract class Expr {
  public interface Visitor<R> {
    R visitAssignExpr(Assign expr);
    R visitBinaryExpr(Binary expr);
    R visitCallExpr(Call expr);
    R visitFunctionExpr(Function expr);
    R visitGetExpr(Get expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitLogicalExpr(Logical expr);
    R visitObjectLiteralExpr(ObjectLiteral expr);
    R visitSetExpr(Set expr);
    R visitThisExpr(This expr);
    R visitUnaryExpr(Unary expr);
    R visitVariableExpr(Variable expr);
  }

  public static class Assign extends Expr {
    public Assign(Symbol name, Expr value) {
      this.name = name;
      this.value = value;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitAssignExpr(this);
    }

    public final Symbol name;
    public final Expr value;
  }

  public static class Binary extends Expr {
    public Binary(Expr left, Operator operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }

    public final Expr left;
    public final Operator operator;
    public final Expr right;
  }

  public static class Call extends Expr {
    public Call(Expr callee, SourceLocation location, List<Expr> arguments) {
      this.callee = callee;
      this.location = location;
      this.arguments = arguments;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitCallExpr(this);
    }

    public final Expr callee;
    public final SourceLocation location;
    public final List<Expr> arguments;
  }

  public static class Function extends Expr {
    public Function(List<Symbol> params, List<Stmt> body) {
      this.params = params;
      this.body = body;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionExpr(this);
    }

    public final List<Symbol> params;
    public final List<Stmt> body;
  }

  public static class Get extends Expr {
    public Get(Expr object, Symbol name) {
      this.object = object;
      this.name = name;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGetExpr(this);
    }

    public final Expr object;
    public final Symbol name;
  }

  public static class Grouping extends Expr {
    public Grouping(Expr expression) {
      this.expression = expression;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }

    public final Expr expression;
  }

  public static class Literal extends Expr {
    public Literal(Object value) {
      this.value = value;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }

    public final Object value;
  }

  public static class Logical extends Expr {
    public Logical(Expr left, Operator operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLogicalExpr(this);
    }

    public final Expr left;
    public final Operator operator;
    public final Expr right;
  }

  public static class ObjectLiteral extends Expr {
    public ObjectLiteral(List<Symbol> keys, List<Expr> values) {
      this.keys = keys;
      this.values = values;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitObjectLiteralExpr(this);
    }

    public final List<Symbol> keys;
    public final List<Expr> values;
  }

  public static class Set extends Expr {
    public Set(Expr object, Symbol name, Expr value) {
      this.object = object;
      this.name = name;
      this.value = value;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSetExpr(this);
    }

    public final Expr object;
    public final Symbol name;
    public final Expr value;
  }

  public static class This extends Expr {
    public This(Symbol keyword) {
      this.keyword = keyword;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitThisExpr(this);
    }

    public final Symbol keyword;
  }

  public static class Unary extends Expr {
    public Unary(Operator operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }

    public final Operator operator;
    public final Expr right;
  }

  public static class Variable extends Expr {
    public Variable(Symbol name) {
      this.name = name;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitVariableExpr(this);
    }

    public final Symbol name;
  }

  public abstract <R> R accept(Visitor<R> visitor);
}
