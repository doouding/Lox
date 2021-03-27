package com.interpreter.lox;

public class Terminate extends RuntimeException {
    final Token token;

    Terminate(Token token) {
        super(null, null, false, false);
        this.token = token;
    }
}
