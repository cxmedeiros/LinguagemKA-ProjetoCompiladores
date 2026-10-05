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
    // Vira true quando o Scanner ou o Parser reporta qualquer erro.
    static boolean hadError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Uso: ka [arquivo]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, StandardCharsets.UTF_8));

        if (hadError) System.exit(65);
    }

    private static void runPrompt() throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

        for (;;) {
            System.out.print("ka> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;   // um erro no prompt nao deve travar a sessao
        }
    }

    private static void run(String source) {
        // 1) Analise lexica
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        System.out.println("=== Tokens ===");
        for (Token token : tokens) {
            System.out.println(token);
        }

        // Se o Scanner achou erro, os tokens estao incompletos: parsear so
        // geraria erros em cascata.
        if (hadError) return;

        // 2) Analise sintatica
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Parou por erro sintatico: as mensagens ja foram impressas.
        if (hadError) return;

        System.out.println("=== Parser ===");
        System.out.print(new AstPrinter().print(statements));
        System.out.println("Analise sintatica concluida sem erros: "
            + statements.size() + " declaracao(oes).");
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

    private static void report(int line, String where, String message) {
        System.err.println("[linha " + line + "] Erro" + where + ": " + message);
        hadError = true;
    }
}
