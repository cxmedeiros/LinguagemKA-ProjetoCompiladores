package ka.ast;

public final class FieldPattern {
  public FieldPattern(String name, Pattern subPattern, String binding) {
    this.name = name;
    this.subPattern = subPattern;
    this.binding = binding;
  }

  public final String name;
  public final Pattern subPattern;
  public final String binding;
}
