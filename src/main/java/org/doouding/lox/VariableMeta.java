package org.doouding.lox;

public class VariableMeta {
    public boolean hasAccessed;
    public boolean hasInitialized;
    public Token name;

    VariableMeta(Token name, boolean hasAccessed, boolean hasInitialized) {
        this.name = name;
        this.hasAccessed = hasAccessed;
        this.hasInitialized = hasInitialized;
    }
}
