package ka;

import java.util.List;

import ka.ast.Stmt;
import ka.tac.TacBuilder;
import ka.tac.TacProgram;

/** Testes de traducao de expressoes e fluxo TAC, sem executar a linguagem. */
public final class TacBuilderTest {
    public static void main(String[] args) {
        TacProgram expression = compile("print 2 + 3 * 4;");
        String expected = "t1 = 3 * 4\nt2 = 2 + t1\nprint t2\n";
        if (!expected.equals(expression.toString())) {
            throw new AssertionError("TAC de precedencia inesperado:\n" + expression);
        }

        TacProgram flow = compile(
            "var i = 0; while (i < 2) { if (i == 0) print i; i = i + 1; }"
        );
        String text = flow.toString();
        if (!text.contains("ifFalse ") || !text.contains("goto L")
            || count(text, "L1:") != 1 || count(text, "L2:") != 1) {
            throw new AssertionError("TAC de fluxo incompleto:\n" + text);
        }

        TacProgram scopes = compile("{ var x = 1; print x; { var x = 2; print x; } print x; }");
        String scopedText = scopes.toString();
        if (!scopedText.contains("x$1 = 1") || !scopedText.contains("x$2 = 2")
            || count(scopedText, "print x$1") != 2 || count(scopedText, "print x$2") != 1) {
            throw new AssertionError("Bindings locais nao foram distinguidos:\n" + scopedText);
        }

        TacProgram nestedLoops = compile(
            "var i = 0; while (i < 2) { var j = 0; "
            + "while (j < 2) { j = j + 1; } i = i + 1; }"
        );
        String nestedText = nestedLoops.toString();
        for (String label : new String[] {"L1:", "L2:", "L3:", "L4:"}) {
            if (count(nestedText, label) != 1) {
                throw new AssertionError("Rotulos de lacos aninhados invalidos:\n" + nestedText);
            }
        }

        TacProgram logical = compile("print false and 1;");
        String logicalExpected = "t1 = false\nifFalse false goto L1\nt1 = 1\nL1:\nprint t1\n";
        if (!logicalExpected.equals(logical.toString())) {
            throw new AssertionError("Curto-circuito logico inesperado:\n" + logical);
        }

        TacProgram objectAssignment = compile("var obj = nil; obj.x = (obj = {});");
        String objectText = objectAssignment.toString();
        if (objectText.indexOf("check_object") < 0
            || objectText.indexOf("check_object") > objectText.indexOf("create_object")) {
            throw new AssertionError("A atribuicao de campo deve validar o receptor antes do RHS:\n"
                + objectText);
        }

        TacProgram selection = compile(
            "switch (1) { case 1: print \"one\"; case 2: print \"two\"; default: print \"other\"; }"
        );
        String selectionText = selection.toString();
        if (count(selectionText, "ifTrue") != 2 || count(selectionText, "goto L") < 4
            || !selectionText.contains("print \"other\"")) {
            throw new AssertionError("TAC de switch incompleto:\n" + selectionText);
        }

        TacProgram functions = compile(
            "fun soma(a, b) { return a + b; } print soma(2, 3);"
        );
        String functionText = functions.toString();
        String functionExpected = "t3 = closure fn1_soma\nsoma = t3\nt4 = soma\n"
            + "check_callable t4\ncheck_arity t4, 2\nparam 2\nparam 3\n"
            + "t5 = call t4, 2\nprint t5\n\nfn1_soma:\nthis = @this\n"
            + "a$1 = @parameter0\nb$2 = @parameter1\nt1 = a$1\n"
            + "t2 = t1 + b$2\nreturn t2\nreturn nil\n";
        if (!functionExpected.equals(functionText)) {
            throw new AssertionError("TAC dourado de funcao/chamada inesperado:\n" + functionText);
        }
        if (!functionText.contains("closure fn1_soma")
            || !functionText.contains("param 2\nparam 3\n")
            || !functionText.contains("call ")
            || !functionText.contains("@parameter0")
            || !functionText.contains("return t")) {
            throw new AssertionError("TAC de funcao/chamada incompleto:\n" + functionText);
        }

        TacProgram closures = compile(
            "fun fabrica() { var x = 0; fun proxima() { x = x + 1; return x; } return proxima; }"
        );
        String closureText = closures.toString();
        if (closureText.indexOf("fn1_fabrica:") < 0
            || closureText.indexOf("fn2_proxima:") < closureText.indexOf("fn1_fabrica:")
            || !closureText.contains("captures (x$")
            || !closureText.contains("u0 = @upvalue0")
            || !closureText.contains("t1 + 1") || !closureText.contains("u0 = t2")) {
            throw new AssertionError("TAC de closure/upvalue incompleto:\n" + closureText);
        }

        TacProgram forwardedClosure = compile(
            "fun fabrica() { var x = 0; fun meio() { "
            + "fun proxima() { x = x + 1; return x; } return proxima; } return meio; }"
        );
        String forwardedText = forwardedClosure.toString();
        if (!forwardedText.contains("fn3_proxima:")
            || !forwardedText.contains("closure fn3_proxima captures (u0)")) {
            throw new AssertionError("Upvalue nao foi encaminhado pela closure intermediaria:\n"
                + forwardedText);
        }

        TacProgram methods = compile(
            "var obj = { x: 4, m: fun(a) { return this.x + a; } }; print obj.m(2);"
        );
        if (!methods.toString().contains(", this ")) {
            throw new AssertionError("A chamada de metodo nao preservou o receptor this:\n" + methods);
        }

        TacProgram matching = compile(
            "var obj = { x: 12, y: 2 }; "
            + "print match (obj) { case { x: Number(n), y } => n + y; case _ => 0; };"
        );
        String matchText = matching.toString();
        if (!matchText.contains("is_type") || !matchText.contains("hasprop")
            || !matchText.contains("getprop") || !matchText.contains("match_error")
            || !matchText.contains("n$") || !matchText.contains("y$")) {
            throw new AssertionError("TAC de pattern matching incompleto:\n" + matchText);
        }

        TacProgram literalMatch = compile("print match (2) { case 2 => 4; case _ => 5; };");
        String matchExpected = "t1 = 2\nt3 = t1 == 2\nifTrue t3 goto L2\ngoto L3\n"
            + "L2:\nt2 = 4\ngoto L1\nL3:\ngoto L4\nL4:\nt2 = 5\n"
            + "goto L1\nL5:\nmatch_error t1 at linha 1, coluna 7\nL1:\nprint t2\n";
        if (!matchExpected.equals(literalMatch.toString())) {
            throw new AssertionError("TAC dourado de match inesperado:\n" + literalMatch);
        }
    }

    private static TacProgram compile(String source) {
        Ka.hadError = false;
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        if (Ka.hadError) throw new AssertionError("Falha lexical no teste.");
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();
        if (Ka.hadError) throw new AssertionError("Falha sintatica no teste.");
        Resolver resolver = new Resolver();
        resolver.resolve(statements);
        if (Ka.hadError) throw new AssertionError("Falha semantica no teste.");
        return new TacBuilder().build(statements, resolver.locals());
    }

    private static int count(String text, String fragment) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(fragment, at)) >= 0) {
            count++;
            at += fragment.length();
        }
        return count;
    }
}
