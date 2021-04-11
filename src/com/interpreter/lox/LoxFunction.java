package com.interpreter.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Enviroment cloure;

    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Enviroment enviroment, boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.cloure = enviroment;
        this.declaration = declaration;
    }

    LoxFunction bind(LoxInstance instance) {
        Enviroment enviroment = new Enviroment(cloure);
        enviroment.define("this", instance);
        return new LoxFunction(declaration, enviroment, isInitializer);
    }

    @Override
    public String toString() {
        return "<fn" + declaration.name.lexeme + ">";
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Enviroment enviroment = new Enviroment(cloure);
        for(int i = 0; i < declaration.params.size(); i++) {
            enviroment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, enviroment);
        } catch(Return returnValue) {
            return returnValue.value;
        }

        return null;
    }


}
