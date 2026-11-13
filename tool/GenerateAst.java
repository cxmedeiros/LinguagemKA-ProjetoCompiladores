package tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/*
 * Gera Expr.java e Stmt.java (os nos da AST).
 *
 * Nenhum no guarda Token - a AST nao depende da fase lexica:
 *   Symbol         -> nomes (variavel, funcao, parametro, propriedade, "this")
 *   Operator       -> operadores de Binary, Logical e Unary
 *   SourceLocation -> quando o no so precisa da posicao pra reportar erro
 *                     (o ")" de uma chamada, a palavra "return")
 *
 * Uso (a partir da raiz do projeto):
 *   java tool/GenerateAst.java [diretorio de saida]
 *   (padrao: Ka/ast)
 *
 * Expr e Stmt ficam no pacote ka.ast, separados do
 * resto, porque sao gerados: nao se edita Expr.java/Stmt.java a mao.
 */
public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.err.println("Uso: generate_ast [diretorio de saida]");
            System.exit(64);
        }
        String outputDir = args.length == 1
            ? args[0]
            : "Ka/ast";

        defineAst(outputDir, "Expr", Arrays.asList(
            "Assign        : Symbol name, Expr value",
            "Binary        : Expr left, Operator operator, Expr right",
            "Call          : Expr callee, SourceLocation location, List<Expr> arguments",
            "Function      : List<Symbol> params, List<Stmt> body",
            "Get           : Expr object, Symbol name",
            "Grouping      : Expr expression",
            "Literal       : Object value",
            "Logical       : Expr left, Operator operator, Expr right",
            "ObjectLiteral : List<Symbol> keys, List<Expr> values",
            "Set           : Expr object, Symbol name, Expr value",
            "This          : Symbol keyword",
            "Unary         : Operator operator, Expr right",
            "Variable      : Symbol name",
            // location = a palavra "match" (onde reportar "nenhum caso casou")
            "Match         : SourceLocation location, Expr scrutinee, List<MatchCase> cases"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
            "Block      : List<Stmt> statements",
            "Expression : Expr expression",
            "Function   : Symbol name, List<Symbol> params, List<Stmt> body",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "Print      : Expr expression",
            "Return     : SourceLocation location, Expr value",
            // switch (subject) { case caseValues[i]: caseBodies[i] ... default: defaultBranch }
            // caseValues e caseBodies andam em paralelo (como keys/values do ObjectLiteral);
            // defaultBranch e null quando nao ha "default".
            "Switch     : Expr subject, List<Expr> caseValues, List<List<Stmt>> caseBodies, List<Stmt> defaultBranch",
            "Var        : Symbol name, Expr initializer",
            "While      : Expr condition, Stmt body"
        ));

        defineAst(outputDir, "Pattern", Arrays.asList(
            "Literal       : Object value",
            "Wildcard      : ",
            // location = o binding (ou o nome do tipo, se nao houver binding)
            "TypePattern   : String typeName, String binding, SourceLocation location",
            "ObjectPattern : List<FieldPattern> fields"
        ));

        defineDataClass(
            outputDir,
            "MatchCase",
            "Pattern pattern, Expr body"
        );

        defineDataClass(
            outputDir,
            "FieldPattern",
            // location = o nome do campo
            "String name, Pattern subPattern, String binding, SourceLocation location"
        );
    }

    private static void defineAst(String outputDir, String baseName, List<String> types)
            throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package ka.ast;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("import ka.Operator;");
        writer.println("import ka.SourceLocation;");
        writer.println("import ka.Symbol;");
        writer.println();
        writer.println("public abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);

       for (String type : types) {
            String[] parts = type.split(":", 2);

            String className = parts[0].trim();
            String fields = parts[1].trim();

            defineType(writer, baseName, className, fields);
        }

        writer.println();
        writer.println("  public abstract <R> R accept(Visitor<R> visitor);");
        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("  public interface Visitor<R> {");
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" +
                typeName + " " + baseName.toLowerCase() + ");");
        }
        writer.println("  }");
    }

    private static void defineType(PrintWriter writer, String baseName,
                                    String className, String fieldList) {
        writer.println();
        writer.println("  public static class " + className + " extends " + baseName + " {");
        writer.println("    public " + className + "(" + fieldList + ") {");
        String[] fields = fieldList.isEmpty()
            ? new String[0]
            : fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("      this." + name + " = " + name + ";");
        }
        writer.println("    }");
        writer.println();
        writer.println("    public <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");
        writer.println();
        for (String field : fields) {
            writer.println("    public final " + field + ";");
        }
        writer.println("  }");
    }
    
    private static void defineDataClass(
            String outputDir,
            String className,
            String fieldList) throws IOException {

        String path = outputDir + "/" + className + ".java";

        try (PrintWriter writer = new PrintWriter(path, "UTF-8")) {
            writer.println("package ka.ast;");
            writer.println();
            writer.println("import ka.SourceLocation;");
            writer.println();
            writer.println("public final class " + className + " {");

            String[] fields = fieldList.split(", ");

            writer.println("  public " + className +
                        "(" + fieldList + ") {");

            for (String field : fields) {
                String name = field.substring(field.lastIndexOf(' ') + 1);
                writer.println("    this." + name + " = " + name + ";");
            }

            writer.println("  }");
            writer.println();

            for (String field : fields) {
                writer.println("  public final " + field + ";");
            }

            writer.println("}");
        }
    }

}
