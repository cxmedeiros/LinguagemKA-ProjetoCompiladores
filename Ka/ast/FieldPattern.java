package ka.ast;

import ka.SourceLocation;

public final class FieldPattern {
  public FieldPattern(String name, Pattern subPattern, String binding, SourceLocation location) {
    this.name = name;
    this.subPattern = subPattern;
    this.binding = binding;
    this.location = location;
  }

  public final String name;
  public final Pattern subPattern;
  public final String binding;
  public final SourceLocation location;
}
