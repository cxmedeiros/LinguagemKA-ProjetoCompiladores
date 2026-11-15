package ka.tac;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Confere que todos os programas de exemplo passam pelo gerador e validador. */
public final class TacCorpusTest {
    public static void main(String[] args) throws Exception {
        Path cases = Paths.get("test", "casos");
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cases, "*.ka")) {
            for (Path file : stream) files.add(file);
        }
        Collections.sort(files);
        for (Path file : files) {
            Result result = run(file);
            if (result.exitCode == 65) {
                if (!result.stdout.isEmpty() || result.stderr.isEmpty()) {
                    throw new AssertionError(file + " deve deixar stdout vazio em erro do front-end.");
                }
            } else if (result.exitCode != 0 || !result.stderr.isEmpty()) {
                throw new AssertionError(file + " falhou no TAC: exit=" + result.exitCode
                    + ", stderr=" + result.stderr);
            }
        }
        System.out.println(files.size() + " programas aceitos pelo gerador TAC.");
    }

    private static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;
        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static Result run(Path file) throws Exception {
        String java = new File(new File(System.getProperty("java.home"), "bin"),
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")
            .getAbsolutePath();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            "ka.Ka", "--tac", file.toString())
            .directory(new File(System.getProperty("user.dir"))).start();
        String stdout = read(process.getInputStream());
        String stderr = read(process.getErrorStream());
        return new Result(process.waitFor(), stdout, stderr);
    }

    private static String read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
