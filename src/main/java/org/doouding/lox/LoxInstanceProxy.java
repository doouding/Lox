package org.doouding.lox;

public class LoxInstanceProxy {
    private LoxInstance instance;
    private boolean allowPrivate;

    LoxInstanceProxy(LoxInstance instance, boolean allowPrivate) {
        this.instance = instance;
        this.allowPrivate = allowPrivate;
    }

    void set(Token name, Object value) {
        this.instance.set(name, value, this.allowPrivate);
    }

    Object get(Token name) {
        return this.instance.get(name, this.allowPrivate);
    }

    @Override
    public String toString() {
        return this.instance.toString();
    }
}
