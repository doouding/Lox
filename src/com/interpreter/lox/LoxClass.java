package com.interpreter.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable {
    final String name;
    private final Map<String, LoxField> fields;
    private final Map<String, LoxField> privateFields;

    private final Map<String, LoxFunction> methods;
    private final Map<String, LoxFunction> privateMethods;
    private final Map<String, LoxFunction> staticMethods;

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    LoxClass(String name,
        Map<String, LoxFunction> methods,
        Map<String, LoxFunction> staticMethods,
        Map<String, LoxFunction> privateMethods,
        Map<String, LoxField> fields,
        Map<String, LoxField> privateFields
    ) {
        this.name = name;

        this.methods = methods;
        this.staticMethods = staticMethods;
        this.privateMethods = privateMethods;

        this.fields = fields;
        this.privateFields = privateFields;
    }

    boolean hasMethod(String name) {
        return methods.containsKey(name);
    }

    boolean hasPrivateMethod(String name) {
        return privateMethods.containsKey(name);
    }

    LoxFunction findPrivateMethod(String name) {
        if (privateMethods.containsKey(name)) {
            return privateMethods.get(name);
        }

        return null;
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Map<String, Object> fields = new HashMap<String, Object>();
        Map<String, Object> privateFields = new HashMap<String, Object>();

        for(Map.Entry<String, LoxField> field: this.fields.entrySet()) {
            fields.put(field.getKey(), null);
        }
        for(Map.Entry<String, LoxField> field: this.privateFields.entrySet()) {
            privateFields.put(field.getKey(), null);
        }

        LoxInstance instance = new LoxInstance(this, privateFields, fields);
        LoxInstanceProxy instanceProxy = new LoxInstanceProxy(instance, false);
        LoxFunction initializer = findMethod("init");

        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instanceProxy;
    }

    public Object getStatic(Token name) {
        if(staticMethods.containsKey(name.lexeme)) {
            return staticMethods.get(name.lexeme);
        }

        throw new RuntimeError(name, "Cannot find static method " + name.lexeme);
    }
}
