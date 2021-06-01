package org.doouding.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    /**
     * 全局 Enviroment
     */
    final Enviroment globals = new Enviroment();

    /**
     * 当前执行代码所处的 Enviroment
     */
    private Enviroment enviroment = globals;

    /**
     * 变量所处的 Enviroment 深度
     */
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.define("clock", new LoxCallable() {
           @Override
           public int arity() {
               return 0;
            }
           
           @Override
           public Object call(Interpreter interpreter, List<Object> arguments) {
               return (double)System.currentTimeMillis() / 1000.0;
           }

           @Override
           public String toString() { return "<native fn>"; }
        });
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstanceProxy)) {
            throw new RuntimeError(expr.name, "Only instances have fields");
        }

        Object value = evaluate(expr.value);
        ((LoxInstanceProxy)object).set(expr.name, value);

        return null;
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);

        if (object instanceof LoxInstanceProxy) {
            return ((LoxInstanceProxy) object).get(expr.name);
        }

        if (object instanceof LoxClass) {
            return ((LoxClass) object).getStatic(expr.name);
        }

        throw new RuntimeError(expr.name,
            "Only instances have properties.");
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        enviroment.define(stmt.name.lexeme, null);

        Map<String, LoxFunction> methods = new HashMap<>();
        Map<String, LoxFunction> staticMethods = new HashMap<>();
        Map<String, LoxFunction> privateMethods = new HashMap<>();
        Map<String, LoxField> fields = new HashMap<>();
        Map<String, LoxField> privateFields = new HashMap<>();

        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, enviroment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        for (Stmt.Function method : stmt.staticMethods) {
            LoxFunction function = new LoxFunction(method, enviroment, false);
            staticMethods.put(method.name.lexeme, function);
        }

        for (Stmt.Function method : stmt.privateMethods) {
            LoxFunction function = new LoxFunction(method, enviroment, false);
            privateMethods.put(method.name.lexeme, function);
        }

        for (Expr.Variable field: stmt.fields) {
            fields.put(field.name.lexeme, new LoxField(field.name));
        }
        for (Expr.Variable field: stmt.privateFields) {
            privateFields.put(field.name.lexeme, new LoxField(field.name));
        }

        LoxClass klass = new LoxClass(
            stmt.name.lexeme,
            methods,
            staticMethods,
            privateMethods,
            fields,
            privateFields
        );
        enviroment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if(stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, enviroment, false);
        enviroment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<Object>();
        for (Expr argument: expr.arguments) {
            arguments.add(evaluate(argument));
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                function.arity() + " arguments but got " +
                arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Void visitTerminateStmt(Stmt.Terminate statement) {
        throw new Terminate(statement.identifier);
    }

    @Override
    public Void visitWhileStmt(Stmt.While statement) {
        while(isTruthy(evaluate(statement.condition))) {
            try {
                execute(statement.loopStatement);
            } catch(Terminate e) {
                if(e.token.type == TokenType.BREAK) {
                    break;
                }
                else if (e.token.type == TokenType.CONTINUE) {
                    continue;
                }
            }
        }

        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);
        boolean leftTruethy = isTruthy(left);

        if(expr.operator.type == TokenType.AND) {
            if(leftTruethy) {
                return evaluate(expr.right);
            }

            return left;
        }
        else if(leftTruethy){
            return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Void visitIfStmt(Stmt.If statement) {
        Object value = evaluate(statement.condition);

        if(isTruthy(value)){
            execute(statement.thenBranch);
        }
        else if(statement.elseBranch != null) {
            execute(statement.elseBranch);
        }

        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            enviroment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Enviroment(enviroment));
        return null;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return !isTruthy(right);
            default:
                return null;
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            default:
                return null;
        }
    }

    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {
        Object condition = evaluate(expr.condition);

        if(isTruthy(condition)) {
            return evaluate(expr.stat1);
        }
        else {
            return evaluate(expr.stat2);
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression exprStmt) {
        evaluate(exprStmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if(stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        enviroment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitSelfOpExpr(Expr.SelfOp expr) {
        Object variable = lookUpVariable(expr.name, expr);

        checkNumberOperand(expr.operator, variable);
        Integer distance = locals.get(expr);
        Double calculatedValue = expr.operator.type == TokenType.DECREMENT
            ? (Double)variable - 1
            : (Double)variable + 1;

        if (distance != null) {
            enviroment.assignAt(distance, expr.name, calculatedValue);
        }
        else {
            globals.assign(expr.name, calculatedValue);
        }

        return expr.left ? calculatedValue : variable;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);

        if (distance != null) {
            return enviroment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    void executeBlock(List<Stmt> statements, Enviroment environment) {
        Enviroment previous = this.enviroment;

        try {
            this.enviroment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.enviroment = previous;
        }
    }

    public boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null & b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if(left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers");
    }

    /**
     * evaluate statements
     * @param statements Expression to evaluate
     */
    void interprete(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }

            return text;
        }

        return object.toString();
    }
}