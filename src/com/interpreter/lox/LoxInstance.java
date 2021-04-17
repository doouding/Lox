package com.interpreter.lox;

import java.util.HashMap;
import java.util.Map;

public class LoxInstance {
    private LoxClass klass;
    private Map<String, Object> fields = new HashMap<>();
    private Map<String, Object> privateFields = new HashMap<>();

    LoxInstance(LoxClass klass, Map<String, Object> privateFields, Map<String, Object> fields) {
        this.klass = klass;
        this.fields = fields;
        this.privateFields = privateFields;
    }

    void set(Token name, Object value, boolean allowPrivate) {
        if(privateFields.containsKey(name.lexeme)) {
            if(allowPrivate) {
                privateFields.put(name.lexeme, value);
                return;
            }
            else {
                throw new RuntimeError(name, "Cannot set private property \"" + name.lexeme + " \" outside the class");
            }
        }

        if(fields.containsKey(name.lexeme)) {
            fields.put(name.lexeme, value);
            return;
        }

        throw new RuntimeError(name, "Cannot set field '" + name.lexeme + "' 'cause it's undefined");
    }

    Object get(Token name, boolean allowPrivate) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        if(privateFields.containsKey(name.lexeme)) {
            if (allowPrivate) {
                return fields.get(name.lexeme);
            }
            else {
                throw new RuntimeError(name, "Cannot access the private field \"" + name.lexeme + "\" outside the class.");
            }
        }

        if (klass.hasMethod(name.lexeme)) {
            return klass.findMethod(name.lexeme).bind(this);
        }
        if (klass.hasPrivateMethod(name.lexeme)) {
            if(allowPrivate) {
                return klass.findPrivateMethod(name.lexeme).bind(this);
            }
            else {
                throw new RuntimeError(name, "Can't access the private method \"" + name.lexeme + "\" outside the class.");
            }
        }

        throw new RuntimeError(name,
            "Undefined property '" + name.lexeme + "'.");
    }

    @Override
    public String toString() {
        return klass.name + " instance" ;
    }
}
