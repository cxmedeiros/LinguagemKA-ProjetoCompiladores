package ka.ast;

import java.util.List;

import ka.Operator;
import ka.SourceLocation;
import ka.Symbol;

public abstract class Pattern {
  public interface Visitor<R> {
    R visitLiteralPattern(Literal pattern);
    R visitWildcardPattern(Wildcard pattern);
    R visitTypePatternPattern(TypePattern pattern);
    R visitObjectPatternPattern(ObjectPattern pattern);
  }

  public static class Literal extends Pattern {
    public Literal(Object value) {
      this.value = value;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralPattern(this);
    }

    public final Object value;
  }

  public static class Wildcard extends Pattern {
    public Wildcard() {
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitWildcardPattern(this);
    }

  }

  public static class TypePattern extends Pattern {
    public TypePattern(String typeName, String binding) {
      this.typeName = typeName;
      this.binding = binding;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitTypePatternPattern(this);
    }

    public final String typeName;
    public final String binding;
  }

  public static class ObjectPattern extends Pattern {
    public ObjectPattern(List<FieldPattern> fields) {
      this.fields = fields;
    }

    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitObjectPatternPattern(this);
    }

    public final List<FieldPattern> fields;
  }

  public abstract <R> R accept(Visitor<R> visitor);
}
