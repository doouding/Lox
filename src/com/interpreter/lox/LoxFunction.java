package com.interpreter.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Enviroment cloure;

    LoxFunction(Stmt.Function declaration, Enviroment enviroment) {
        this.cloure = enviroment;
        this.declaration = declaration;
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
