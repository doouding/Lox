package org.doouding.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, VariableMeta>> scopes = new Stack<>();
    private FunctionType currentFunctionType = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;
    private Stmt.Class currentClassStmt = null;
    private boolean insideLoop = false;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum ClassType {
        NONE,
        CLASS
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        INITIALIZER,
        STATIC_METHOD
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'this' outside of a class.");
            return null;
        }

        if (currentFunctionType == FunctionType.STATIC_METHOD) {
            Lox.error(expr.keyword, "The keyword \"this\" can only use inside of a class method");
            return null;
        }

        resolveLocal(expr, expr.keyword, true);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        if(expr.object instanceof Expr.This && currentClass != ClassType.NONE) {
            boolean existFlag = false;

            for(int i = 0; i < currentClassStmt.privateFields.size(); i++) {
                if(currentClassStmt.privateFields.get(i).name.lexeme.equals(expr.name.lexeme)) {
                    existFlag = true;
                    break;
                }
            }

            if(!existFlag) {
                for(int i = 0; i < currentClassStmt.fields.size(); i++) {
                    if(currentClassStmt.fields.get(i).name.lexeme.equals(expr.name.lexeme)) {
                        existFlag = true;
                        break;
                    }
                }
            }

            if(!existFlag) {
                Lox.error(expr.name, "Cannot set field value without declaration");
                return null;
            }
        }

        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        Stmt.Class enclosingClassStmt = currentClassStmt;
        currentClass = ClassType.CLASS;
        currentClassStmt = stmt;

        declare(stmt.name);
        define(stmt.name);

        beginScope();
        scopes.peek().put("this", new VariableMeta(new Token(TokenType.THIS, "this", null, 0), false, true));

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = method.name.lexeme == "init"
                ? FunctionType.INITIALIZER
                : FunctionType.METHOD;
            resolveFunction(method, declaration);
        }

        scopes.peek().remove("this");

        for (Stmt.Function method : stmt.staticMethods) {
            resolveFunction(method, FunctionType.STATIC_METHOD);
        }

        endScope();
        currentClass = enclosingClass;
        currentClassStmt = enclosingClassStmt;

        return null;
    }

    @Override
    public Void visitConditionalExpr(Expr.Conditional expr) {
        resolve(expr.condition);
        resolve(expr.stat1);
        resolve(expr.stat2);

        return null;
    }

    @Override
    public Void visitTerminateStmt(Stmt.Terminate stmt) {
        if(insideLoop == false) {
            Lox.error(stmt.identifier, stmt.identifier.lexeme + " must used inside loop");
        }

        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitSelfOpExpr(Expr.SelfOp expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme).hasInitialized) {
            Lox.error(expr.name, "Cannot read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name, true);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        resolveLocal(expr, expr.name, true);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);

        if(stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunctionType == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunctionType == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Cannot return from init function");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        boolean encloseLoop = insideLoop;
        insideLoop = true;
        resolve(stmt.loopStatement);
        insideLoop = encloseLoop;

        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);

        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunctionType = currentFunctionType;
        currentFunctionType = type;

        beginScope();

        for (Token param: function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        currentFunctionType = enclosingFunctionType;

        endScope();
    }

    /**
     * Resolve a local variable.
     * @param expr the expression that contains the variable
     * @param name the variable name
     * @param isAccess whether the variable is accessed, false value indicate this is an assignment
     */
    private void resolveLocal(Expr expr, Token name, Boolean isAccess) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                if(isAccess) {
                    scopes.get(i).get(name.lexeme).hasAccessed = true;
                }
                if(scopes.get(i).get(name.lexeme).hasInitialized == false) {
                    Lox.error(name.line, "Cannot read local variable in its own initializer.");
                }
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void declare(Token name) {
        // We gonna skip the declare when the scopes are empty
        // 'case that means we are in the global scope
        // and variable in global scope is more dynamic so we won't resolve thme
        if (scopes.isEmpty()) return;

        Map<String, VariableMeta> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already variable with this name in this scope.");
        }

        scope.put(name.lexeme, new VariableMeta(name, false, false));
    }

    private void define(Token name) {
        if(scopes.isEmpty()) return;

        VariableMeta meta = scopes.peek().get(name.lexeme);
        meta.hasInitialized = true;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        scopes.push(new HashMap<String, VariableMeta>());
    }

    private void endScope() {
        Map<String, VariableMeta> scope = scopes.pop();

        for(Map.Entry<String, VariableMeta> entry: scope.entrySet()) {
            VariableMeta meta = entry.getValue();
            if (!meta.hasAccessed && meta.name.type != TokenType.THIS) {
                Lox.error(meta.name, "Variable is defined but never used");
            }
        }
    }
}
