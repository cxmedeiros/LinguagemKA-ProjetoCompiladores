package ka;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import ka.ast.Stmt;

public class Ka {
    // Vira true quando o Scanner, o Parser ou o Resolver reporta erro.
    static boolean hadError = false;
    // Vira true quando o Interpreter aborta por erro de execucao.
    static boolean hadRuntimeError = false;

    // --debug: so o front-end (tokens + AST), sem executar o programa.
    private static boolean debug = false;
    // --trace: tokens + AST + EXECUCAO com o passo a passo do interpretador.
    private static boolean trace = false;

    // Instancia unica: no prompt (REPL) as variaveis globais sobrevivem entre linhas.
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        String path = null;
        for (String arg : args) {
            if (arg.equals("--debug")) {
                debug = true;
            } else if (arg.equals("--trace")) {
                trace = true;
                interpreter.trace = true;
            } else if (path == null) {
                path = arg;
            } else {
                System.out.println("Uso: ka [--debug | --trace] [arquivo]");
                System.exit(64);
            }
        }

        if (path != null) {
            runFile(path);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, StandardCharsets.UTF_8));

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

        for (;;) {
            System.out.print("ka> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;           // um erro no prompt nao deve travar a sessao
            hadRuntimeError = false;
        }
    }

    private static void run(String source) {
        // 1) Analise lexica
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        if (debug || trace) {
            System.out.println("=== Tokens ===");
            for (Token token : tokens) {
                System.out.println(token);
            }
        }

        // Se o Scanner achou erro, os tokens estao incompletos: parsear so
        // geraria erros em cascata.
        if (hadError) return;

        // 2) Analise sintatica
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();
        if (hadError) return;

        // 3) Analise semantica de escopo
        Resolver resolver = new Resolver();
        resolver.resolve(statements);
        if (hadError) return;

        if (debug || trace) {
            System.out.println("=== Parser ===");
            System.out.print(new AstPrinter().print(statements));
            System.out.println("Analise sintatica concluida sem erros: "
                + statements.size() + " declaracao(oes).");
            if (debug) return;
            System.out.println("=== Execucao ===");
        }

        // 4) Execucao
        interpreter.interpret(statements, resolver.locals());
    }

    // Erro lexico (so se conhece a linha).
    static void error(int line, String message) {
        report(line, "", message);
    }

    // Erro sintatico (se conhece o token onde o parser parou).
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " no fim", message);
        } else {
            report(token.line, " em '" + token.lexeme + "'", message);
        }
    }

    // Erro semantico (Resolver). A localizacao pode ser null quando a AST
    // nao a guarda (ex: bindings de padrao).
    static void semanticError(SourceLocation location, String message) {
        String where = location == null ? "" : " [" + location + "]";
        System.err.println("Erro semantico" + where + ": " + message);
        hadError = true;
    }

    // Erro de execucao (Interpreter). location pode ser null.
    static void runtimeError(RuntimeError error) {
        String where = error.location == null ? "" : " [" + error.location + "]";
        System.err.println("Erro de runtime" + where + ": " + error.getMessage());
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message) {
        System.err.println("[linha " + line + "] Erro" + where + ": " + message);
        hadError = true;
    }
}
