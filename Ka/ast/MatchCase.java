package ka.ast;

import ka.SourceLocation;

public final class MatchCase {
  public MatchCase(Pattern pattern, Expr body) {
    this.pattern = pattern;
    this.body = body;
  }

  public final Pattern pattern;
  public final Expr body;
}
