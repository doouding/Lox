package org.doouding.lox;

public class LoxField {
    Token name;

    LoxField(Token name, Object value) {
        this.name = name;
    }

    LoxField(Token name) {
        this.name = name;
    }
}
