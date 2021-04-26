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

        throw new RuntimeError(name, "Field \"" + name.lexeme + "\" is not defined in the class \"" + klass.name + "\"");
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
            return klass.findMethod(name.lexeme).bind(new LoxInstanceProxy(this, true));
        }
        if (klass.hasPrivateMethod(name.lexeme)) {
            if(allowPrivate) {
                return klass.findPrivateMethod(name.lexeme).bind(new LoxInstanceProxy(this, true));
            }
            else {
                throw new RuntimeError(name, "Cannot access the private method \"" + name.lexeme + "\" outside the class.");
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
