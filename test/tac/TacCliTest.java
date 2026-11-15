package ka.tac;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Testes de integracao para --tac em arquivo, REPL e erros do front-end. */
public final class TacCliTest {
    private static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;
        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = normalize(stdout);
            this.stderr = normalize(stderr);
        }
    }

    public static void main(String[] args) throws Exception {
        Result generated = runSource("print 2 + 3 * 4;", "--tac");
        expect(0, "t1 = 3 * 4\nt2 = 2 + t1\nprint t2\n", "", generated);

        Result normal = runSource("print 2 + 3;", new String[0]);
        expect(0, "5\n", "", normal);

        for (String invalid : new String[] {"@;", "print ;", "return 1;"}) {
            Result error = runSource(invalid, "--tac");
            if (error.exitCode != 65 || !error.stdout.isEmpty() || error.stderr.isEmpty()) {
                throw new AssertionError("Erro do front-end no modo TAC deve sair com 65 e stdout vazio: "
                    + error.exitCode + ", stdout=" + error.stdout + ", stderr=" + error.stderr);
            }
        }

        Result repl = runPrompt("print 1;\n");
        expect(0, "print 1\n", "", repl);

        Result conflict = runSource("print 1;", "--tac", "--debug");
        if (conflict.exitCode != 64) throw new AssertionError("--tac e --debug devem ser incompativeis.");
    }

    private static Result runSource(String source, String... flags) throws Exception {
        Path path = Files.createTempFile("ka-tac-test-", ".ka");
        try {
            Files.write(path, source.getBytes(StandardCharsets.UTF_8));
            String[] command = new String[flags.length + 1];
            System.arraycopy(flags, 0, command, 0, flags.length);
            command[flags.length] = path.toAbsolutePath().toString();
            return run(command, "");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static Result runPrompt(String input) throws Exception {
        return run(new String[] {"--tac"}, input);
    }

    private static Result run(String[] args, String input) throws Exception {
        String java = new File(new File(System.getProperty("java.home"), "bin"),
            isWindows() ? "java.exe" : "java").getAbsolutePath();
        String[] command = new String[4 + args.length];
        command[0] = java;
        command[1] = "-cp";
        command[2] = System.getProperty("java.class.path");
        command[3] = "ka.Ka";
        System.arraycopy(args, 0, command, 4, args.length);
        Process process = new ProcessBuilder(command).directory(new File(System.getProperty("user.dir"))).start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String stdout = read(process.getInputStream());
        String stderr = read(process.getErrorStream());
        return new Result(process.waitFor(), stdout, stderr);
    }

    private static String read(java.io.InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void expect(int exitCode, String stdout, String stderr, Result actual) {
        if (actual.exitCode != exitCode || !stdout.equals(actual.stdout) || !stderr.equals(actual.stderr)) {
            throw new AssertionError("Resultado CLI inesperado: exit=" + actual.exitCode
                + ", stdout=" + actual.stdout + ", stderr=" + actual.stderr);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String normalize(String value) { return value.replace("\r\n", "\n"); }
}
