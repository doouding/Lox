package com.interpreter.lox;

import java.util.ArrayList;
import java.util.List;

/**
 * program        → declaration* EOF ;
 *
 * declaration    → classDecl
 *                | varDecl
 *                | funDecl
 *                | statement ;
 *
 * classDecl      → "class" IDENTIFIER "{" ( ("private"? IDENTIFIER) | ("static"? function) )* "}" ;
 * verDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 * funDecl        → "fun" function ;
 * function       → IDENTIFIER "(" parameters? ")" block ;
 * parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
 * 
 * statement      → exprStmt
 *                | ifStmt
 *                | printStmt
 *                | whileStmt
 *                | returnStmt
 *                | terminateStmt
 *                | block ;
 *
 * block          → "{" declaration* "}" ;
 * terminateStmt  → "break" | "continue" ;
 * returnStmt     → "return" expression? ";" ;
 * whileStmt      → "while" "(" expression ")" statement;
 * ifStmt         → "if" "(" expression ")" statement
 *                  ("else" statement )? ;
 * printStmt      → "print" expression ";";
 * exprStmt       → expression ";";
 *
 * expression     → equality ;
 * assignment     → ( call "." )? IDENTIFIER "=" assignment
 *                | logic_or
 *
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality )* ;
 * equality       → conditional ( ( "!=" | "==" ) conditional )* ;
 * conditional    → comparison ( "?" conditional ":" conditional )?;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | call ;
 * call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
 * arguments      → expression ( "," expression )
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")" 
 *                | IDENTIFIER ;
 */

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(TokenType.FUN)) return function("function");
            if (match(TokenType.VAR)) return varDeclaration();
            if (match(TokenType.CLASS)) return classDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect class name.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after class name");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> staticMethods = new ArrayList<>();
        List<Stmt.Function> privateMethods = new ArrayList<>();
        List<Expr.Variable> fields = new ArrayList<>();
        List<Expr.Variable> privateFields = new ArrayList<>();

        while(!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            if(check(TokenType.STATIC)) {
                advance();

                staticMethods.add((Stmt.Function)function("staticMethod"));
            }
            else if (check(TokenType.PRIVATE)) {
                advance();
                Object member = classMember("private");

                if(member instanceof Expr.Variable) {
                    privateFields.add((Expr.Variable)member);
                }
                else {
                    privateMethods.add((Stmt.Function)member);
                }
            }
            else {
                Object member = classMember("");

                if(member instanceof Expr.Variable) {
                    fields.add((Expr.Variable)member);
                }
                else {
                    methods.add((Stmt.Function)member);
                }
            }
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body");

        return new Stmt.Class(name, methods, staticMethods, privateMethods, fields, privateFields);
    }

    private Object classMember(String kind) {
        Token identifier = consume(TokenType.IDENTIFIER, "Expect class member name");

        if(check(TokenType.SEMICOLON)) {
            advance();
            return new Expr.Variable(identifier);
        }
        else {
            return finishFunction(kind + "Method", identifier);
        }
    }

    private Stmt finishFunction(String kind, Token name) {
        consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if(!check(TokenType.RIGHT_PAREN)) {
            do {
                if(parameters.size() > 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        consume(TokenType.LEFT_BRACE, "Expect '{' before bodyfun.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private Stmt function(String kind) {
        Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + " name.");

        consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if(!check(TokenType.RIGHT_PAREN)) {
            do {
                if(parameters.size() > 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        consume(TokenType.LEFT_BRACE, "Expect '{' before bodyfun.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(TokenType.EQUAL)) {
            initializer = expression();
        }

        if(shouldConsumeSemicolon()) {
            consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        }

        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.CONTINUE, TokenType.BREAK)) return terminateStatement();
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt terminateStatement() {
        Token token = previous();

        consume(TokenType.SEMICOLON, "Expect ';' after " + token.lexeme + " statement");

        return new Stmt.Terminate(token);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;

        if(!check(TokenType.SEMICOLON)) {
            value = expression();
        }

        consume(TokenType.SEMICOLON, "Expect ';' after return statement");
        return new Stmt.Return(keyword, value);
    }

    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after condition");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition");

        Stmt thenBranchStatement = statement();
        Stmt elseBranchStatement = null;
        if(match(TokenType.ELSE)) {
            elseBranchStatement = statement();
        }

        return new Stmt.If(condition, thenBranchStatement, elseBranchStatement);
    }

    private Stmt whileStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after condition");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition");

        Stmt statements = statement();

        return new Stmt.While(condition, statements);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();

        if(shouldConsumeSemicolon()) {
            consume(TokenType.SEMICOLON, "Expect ';' after expression.");
        }

        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();

        if(shouldConsumeSemicolon()) {
            consume(TokenType.SEMICOLON, "Expect ';' after expression.");
            return new Stmt.Expression(expr);
        }

        return new Stmt.Print(expr);
    }

    private Boolean shouldConsumeSemicolon() {
        return !Lox.isREPL || !isAtEnd();
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();

        if(match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;

                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr and = and();

        while(match(TokenType.OR)) {
            Token operator = previous();
            Expr right = and();
            and = new Expr.Logical(and, operator, right);
        }

        return and;
    }

    private Expr and() {
        Expr equality = equality();

        while(match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            equality = new Expr.Logical(equality, operator, right);
        }

        return equality;
    }

    private Expr equality() {
        Expr expr = conditional();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = conditional();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr conditional() {
        Expr expr = comparison();

        if(match(TokenType.INTERROGATION)) {
            Expr expr1 = conditional();
            consume(TokenType.COLON, "Expect ':' after '?'");
            Expr expr2 = conditional();
            expr = new Expr.Conditional(expr, expr1, expr2);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while(match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while(match(TokenType.SLASH, TokenType.STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Expr unary() {
        if(match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if(match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER,
                    "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
          do {
            if (arguments.size() >= 255) {
                error(peek(), "Cannot have more than 255 arguments");
            }
            arguments.add(expression());
          } while (match(TokenType.COMMA));
        }
    
        Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
    
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if(match(TokenType.FALSE)) return new Expr.Literal(false);
        if(match(TokenType.TRUE)) return new Expr.Literal(true);
        if(match(TokenType.NIL)) return new Expr.Literal(null);

        if(match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if(match(TokenType.THIS)) return new Expr.This(previous());

        if(match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if(match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expec ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if(match(TokenType.BREAK, TokenType.CONTINUE)) {
            Token token = previous();
            throw error(previous(), "Illegal " + token.lexeme + " statement");
        }

        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if(check(type)) return advance();

        throw error(peek(), message);
    }

    /**
     * Check if the current token is of the given type.
     * @param type
     * @return
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if(previous().type == TokenType.SEMICOLON) return;

            switch(peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
