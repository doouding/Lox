package org.doouding.lox;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.LineReader;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    static boolean isREPL = false;

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

        for (;;) {
            try {
                String line = lineReader.readLine("> ");
                if (line == null) break;
                run(line);
                hadError = false;
            } catch (UserInterruptException e) {
                System.exit(0);
            }
        }
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println("[line " + error.token.line + "] RuntimeError at '" + error.token.lexeme + "': " + error.getMessage());
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message) {
        System.err.println(
            "[line " + line + "] Error" + where + ": " + message
        );
        hadError = true;
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        if (hadError) return;

        interpreter.interprete(statements);
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        }
        else if (args.length == 1) {
            isREPL = false;
            runFile(args[0]);
        }
        else {
            isREPL = true;
            runPrompt();
        }
    }
}
