package ka.tac;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ka.Operator;
import ka.ast.Expr;
import ka.ast.FieldPattern;
import ka.ast.MatchCase;
import ka.ast.Pattern;
import ka.ast.Stmt;

/** Tradutor composicional da AST para a IR TAC. */
public final class TacBuilder implements Expr.Visitor<Operand>, Stmt.Visitor<Void> {
    private final List<TacInstruction> instructions = new ArrayList<>();
    private final List<TacFunction> functions = new ArrayList<>();
    private final Map<Expr, Integer> locals = new IdentityHashMap<>();
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private List<TacInstruction> activeInstructions;
    private FunctionContext currentFunction;
    private int temporaryNumber;
    private int labelNumber;
    private int bindingNumber;
    private int functionNumber;

    private static final class Scope {
        final Map<String, Operand> bindings = new HashMap<>();
        final FunctionContext owner;

        Scope(FunctionContext owner) { this.owner = owner; }
    }

    private static final class FunctionContext {
        final String name;
        final int enclosingScopeCount;
        final FunctionContext parent;
        final Map<Operand, Operand> captures = new LinkedHashMap<>();
        Scope frameScope;

        FunctionContext(String name, int enclosingScopeCount, FunctionContext parent) {
            this.name = name;
            this.enclosingScopeCount = enclosingScopeCount;
            this.parent = parent;
        }

        Operand capture(Operand source) {
            Operand upvalue = captures.get(source);
            if (upvalue == null) {
                upvalue = Operand.upvalue(captures.size());
                captures.put(source, upvalue);
            }
            return upvalue;
        }

        List<String> upvalueNames() {
            List<String> names = new ArrayList<>();
            for (Operand upvalue : captures.values()) names.add(upvalue.render());
            return names;
        }

        List<Operand> captureSources() { return new ArrayList<>(captures.keySet()); }
    }

    public TacProgram build(List<Stmt> statements, Map<Expr, Integer> resolvedLocals) {
        instructions.clear();
        functions.clear();
        locals.clear();
        scopes.clear();
        currentFunction = null;
        activeInstructions = instructions;
        temporaryNumber = 0;
        labelNumber = 0;
        bindingNumber = 0;
        functionNumber = 0;
        locals.putAll(resolvedLocals);
        for (Stmt statement : statements) statement.accept(this);
        TacProgram program = new TacProgram(instructions, functions);
        new TacValidator().validate(program);
        return program;
    }

    private Operand temporary() { return Operand.temporary(++temporaryNumber); }
    private Operand label() { return Operand.label("L" + (++labelNumber)); }
    private void emit(TacInstruction instruction) { activeInstructions.add(instruction); }
    private void move(Operand destination, Operand source) {
        emit(TacInstruction.of(Op.MOVE, destination, null, source));
    }
    private Operand snapshot(Operand source) {
        Operand copy = temporary();
        move(copy, source);
        return copy;
    }
    private Operand snapshotIfMutable(Operand source) {
        return source.kind() == Operand.Kind.VARIABLE || source.kind() == Operand.Kind.UPVALUE
            ? snapshot(source) : source;
    }

    private void beginScope() { scopes.push(new Scope(currentFunction)); }
    private void endScope() { scopes.pop(); }

    private Operand binding(String name, int depth) {
        Operand found = scopeAt(depth).bindings.get(name);
        if (found == null) throw new IllegalStateException("Binding TAC ausente: " + name);
        return found;
    }

    private Scope scopeAt(int depth) {
        if (depth < 0 || depth >= scopes.size()) {
            throw new IllegalStateException("Profundidade lexical TAC invalida: " + depth + ".");
        }
        int level = 0;
        for (Scope scope : scopes) if (level++ == depth) return scope;
        throw new IllegalStateException("Escopo TAC ausente.");
    }

    private Operand capture(FunctionContext context, FunctionContext owner, String name, Operand source) {
        Operand valueInParent = source;
        if (context.parent != owner) {
            if (context.parent == null) {
                throw new IllegalStateException("Cadeia de closure inconsistente para " + name + ".");
            }
            valueInParent = capture(context.parent, owner, name, source);
        }
        Operand upvalue = context.capture(valueInParent);
        Operand existing = context.frameScope.bindings.get(name);
        if (existing == null) context.frameScope.bindings.put(name, upvalue);
        else if (!existing.equals(upvalue)) {
            throw new IllegalStateException("Binding capturado conflita com local: " + name);
        }
        return upvalue;
    }

    private Operand variable(Expr expression, String name) {
        Integer depth = locals.get(expression);
        if (depth == null) return Operand.variable(name);
        if (currentFunction != null) {
            int functionScopeCount = scopes.size() - currentFunction.enclosingScopeCount;
            if (depth >= functionScopeCount) {
                Scope declaration = scopeAt(depth);
                return capture(currentFunction, declaration.owner, name, declaration.bindings.get(name));
            }
        }
        return binding(name, depth);
    }

    private String functionName(String declaredName) {
        functionNumber++;
        return "fn" + functionNumber + "_" + (declaredName == null ? "anonymous" : declaredName);
    }

    private FunctionContext compileFunction(String name, List<ka.Symbol> parameters, List<Stmt> body) {
        int functionIndex = functions.size();
        functions.add(null); // Reserva o slot antes das funcoes aninhadas: ordem pre-ordem.

        List<TacInstruction> enclosingInstructions = activeInstructions;
        FunctionContext enclosingFunction = currentFunction;
        int enclosingScopeCount = scopes.size();
        List<TacInstruction> bodyInstructions = new ArrayList<>();
        FunctionContext context = new FunctionContext(name, enclosingScopeCount, enclosingFunction);
        List<String> parameterBindings = new ArrayList<>();
        activeInstructions = bodyInstructions;
        currentFunction = context;
        beginScope();
        try {
            context.frameScope = scopes.peek();
            scopes.peek().bindings.put("this", Operand.variable("this"));
            for (ka.Symbol parameter : parameters) {
                Operand binding = Operand.variable(parameter.name() + "$" + (++bindingNumber));
                scopes.peek().bindings.put(parameter.name(), binding);
                parameterBindings.add(binding.render());
            }
            for (Stmt statement : body) statement.accept(this);
            emit(TacInstruction.of(Op.RETURN, null, null, Operand.constant(null)));
            functions.set(functionIndex,
                new TacFunction(name, parameterBindings, context.upvalueNames(), bodyInstructions));
        } finally {
            endScope();
            activeInstructions = enclosingInstructions;
            currentFunction = enclosingFunction;
        }
        return context;
    }

    private Operand closure(String declaredName, List<ka.Symbol> parameters, List<Stmt> body) {
        FunctionContext context = compileFunction(functionName(declaredName), parameters, body);
        Operand result = temporary();
        emit(new TacInstruction(Op.MAKE_CLOSURE, result, context.captureSources(), context.name));
        return result;
    }

    private void testPattern(Pattern pattern, Operand value, Operand matched, Operand failed,
                             Map<String, Operand> bindings) {
        if (pattern instanceof Pattern.Literal) {
            Operand equal = temporary();
            emit(TacInstruction.of(Op.BINARY, equal, "==", value,
                Operand.constant(((Pattern.Literal) pattern).value)));
            emit(TacInstruction.of(Op.IF_TRUE, null, null, equal, matched));
            emit(TacInstruction.of(Op.GOTO, null, null, failed));
            return;
        }
        if (pattern instanceof Pattern.Wildcard) {
            emit(TacInstruction.of(Op.GOTO, null, null, matched));
            return;
        }
        if (pattern instanceof Pattern.TypePattern) {
            Pattern.TypePattern type = (Pattern.TypePattern) pattern;
            Operand test = temporary();
            emit(TacInstruction.of(Op.TYPE_TEST, test, type.typeName, value));
            emit(TacInstruction.of(Op.IF_TRUE, null, null, test, matched));
            emit(TacInstruction.of(Op.GOTO, null, null, failed));
            if (type.binding != null) bindings.put(type.binding, value);
            return;
        }
        if (pattern instanceof Pattern.ObjectPattern) {
            Operand isObject = temporary();
            Operand fields = label();
            emit(TacInstruction.of(Op.TYPE_TEST, isObject, "Object", value));
            emit(TacInstruction.of(Op.IF_TRUE, null, null, isObject, fields));
            emit(TacInstruction.of(Op.GOTO, null, null, failed));
            emit(TacInstruction.of(Op.LABEL, fields, null));
            for (FieldPattern field : ((Pattern.ObjectPattern) pattern).fields) {
                Operand exists = temporary();
                emit(TacInstruction.of(Op.HAS_PROPERTY, exists, field.name, value));
                emit(TacInstruction.of(Op.IF_FALSE, null, null, exists, failed));
                Operand fieldValue = temporary();
                emit(TacInstruction.of(Op.GET_PROPERTY, fieldValue, field.name, value));
                if (field.subPattern != null) {
                    Operand subPatternMatched = label();
                    testPattern(field.subPattern, fieldValue, subPatternMatched, failed, bindings);
                    emit(TacInstruction.of(Op.LABEL, subPatternMatched, null));
                }
                if (field.binding != null) bindings.put(field.binding, fieldValue);
            }
            emit(TacInstruction.of(Op.GOTO, null, null, matched));
            return;
        }
        throw new IllegalStateException("Padrao TAC desconhecido: " + pattern.getClass().getName());
    }

    private void scoped(List<Stmt> statements) {
        beginScope();
        for (Stmt statement : statements) statement.accept(this);
        endScope();
    }

    @Override public Operand visitLiteralExpr(Expr.Literal expr) {
        return Operand.constant(expr.value);
    }

    @Override public Operand visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    @Override public Operand visitVariableExpr(Expr.Variable expr) {
        return variable(expr, expr.name.name());
    }

    @Override public Operand visitThisExpr(Expr.This expr) {
        return variable(expr, "this");
    }

    @Override public Operand visitAssignExpr(Expr.Assign expr) {
        Operand value = expr.value.accept(this);
        move(variable(expr, expr.name.name()), value);
        return value;
    }

    @Override public Operand visitBinaryExpr(Expr.Binary expr) {
        Operand left = snapshotIfMutable(expr.left.accept(this));
        Operand right = expr.right.accept(this);
        Operand result = temporary();
        emit(TacInstruction.of(Op.BINARY, result, expr.operator.toString(), left, right));
        return result;
    }

    @Override public Operand visitUnaryExpr(Expr.Unary expr) {
        Operand operand = expr.right.accept(this);
        Operand result = temporary();
        emit(TacInstruction.of(Op.UNARY, result, expr.operator.toString(), operand));
        return result;
    }

    @Override public Operand visitLogicalExpr(Expr.Logical expr) {
        Operand left = expr.left.accept(this);
        Operand result = temporary();
        move(result, left);
        Operand end = label();
        boolean isOr = "or".equals(expr.operator.toString());
        emit(TacInstruction.of(isOr ? Op.IF_TRUE : Op.IF_FALSE, null, null, left, end));
        move(result, expr.right.accept(this));
        emit(TacInstruction.of(Op.LABEL, end, null));
        return result;
    }

    @Override public Operand visitGetExpr(Expr.Get expr) {
        Operand object = snapshotIfMutable(expr.object.accept(this));
        Operand result = temporary();
        emit(TacInstruction.of(Op.GET_PROPERTY, result, expr.name.name(), object));
        return result;
    }

    @Override public Operand visitSetExpr(Expr.Set expr) {
        Operand object = snapshotIfMutable(expr.object.accept(this));
        emit(TacInstruction.of(Op.CHECK_OBJECT, null, null, object));
        Operand value = expr.value.accept(this);
        Operand result = temporary();
        emit(TacInstruction.of(Op.SET_PROPERTY, result, expr.name.name(), object, value));
        return result;
    }

    @Override public Operand visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
        Operand object = temporary();
        emit(TacInstruction.of(Op.CREATE_OBJECT, object, null));
        for (int i = 0; i < expr.keys.size(); i++) {
            Operand value = expr.values.get(i).accept(this);
            emit(TacInstruction.of(Op.SET_PROPERTY, temporary(), expr.keys.get(i).name(), object, value));
        }
        return object;
    }

    @Override public Operand visitCallExpr(Expr.Call expr) {
        Operand callee;
        Operand receiver = null;
        if (expr.callee instanceof Expr.Get) {
            Expr.Get get = (Expr.Get) expr.callee;
            receiver = snapshotIfMutable(get.object.accept(this));
            emit(TacInstruction.of(Op.CHECK_OBJECT, null, null, receiver));
            callee = temporary();
            emit(TacInstruction.of(Op.GET_PROPERTY, callee, get.name.name(), receiver));
        } else {
            callee = snapshotIfMutable(expr.callee.accept(this));
        }

        List<Operand> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(snapshotIfMutable(argument.accept(this)));
        }
        emit(TacInstruction.of(Op.CHECK_CALLABLE, null, null, callee));
        emit(TacInstruction.of(Op.CHECK_ARITY, null, null, callee, Operand.constant(arguments.size())));
        for (Operand argument : arguments) emit(TacInstruction.of(Op.PARAM, null, null, argument));

        Operand result = temporary();
        if (receiver == null) {
            emit(TacInstruction.of(Op.CALL, result, null, callee, Operand.constant(arguments.size())));
        } else {
            emit(TacInstruction.of(Op.CALL, result, null,
                callee, Operand.constant(arguments.size()), receiver));
        }
        return result;
    }

    @Override public Operand visitFunctionExpr(Expr.Function expr) {
        return closure(null, expr.params, expr.body);
    }

    @Override public Operand visitMatchExpr(Expr.Match expr) {
        Operand subject = snapshot(expr.scrutinee.accept(this));
        Operand result = temporary();
        Operand end = label();
        for (MatchCase matchCase : expr.cases) {
            Operand matched = label();
            Operand next = label();
            Map<String, Operand> bindings = new LinkedHashMap<>();
            testPattern(matchCase.pattern, subject, matched, next, bindings);
            emit(TacInstruction.of(Op.LABEL, matched, null));
            beginScope();
            for (Map.Entry<String, Operand> entry : bindings.entrySet()) {
                Operand local = Operand.variable(entry.getKey() + "$" + (++bindingNumber));
                scopes.peek().bindings.put(entry.getKey(), local);
                move(local, entry.getValue());
            }
            move(result, matchCase.body.accept(this));
            endScope();
            emit(TacInstruction.of(Op.GOTO, null, null, end));
            emit(TacInstruction.of(Op.LABEL, next, null));
        }
        emit(TacInstruction.of(Op.MATCH_ERROR, null, expr.location.toString(), subject));
        emit(TacInstruction.of(Op.LABEL, end, null));
        return result;
    }

    @Override public Void visitBlockStmt(Stmt.Block stmt) {
        scoped(stmt.statements);
        return null;
    }

    @Override public Void visitVarStmt(Stmt.Var stmt) {
        Operand initializer = stmt.initializer == null ? Operand.constant(null) : stmt.initializer.accept(this);
        Operand destination;
        if (scopes.isEmpty()) {
            destination = Operand.variable(stmt.name.name());
        } else {
            destination = Operand.variable(stmt.name.name() + "$" + (++bindingNumber));
            scopes.peek().bindings.put(stmt.name.name(), destination);
        }
        move(destination, initializer);
        return null;
    }

    @Override public Void visitExpressionStmt(Stmt.Expression stmt) {
        stmt.expression.accept(this);
        return null;
    }

    @Override public Void visitPrintStmt(Stmt.Print stmt) {
        emit(TacInstruction.of(Op.PRINT, null, null, stmt.expression.accept(this)));
        return null;
    }

    @Override public Void visitIfStmt(Stmt.If stmt) {
        Operand condition = stmt.condition.accept(this);
        Operand otherwise = label();
        Operand end = stmt.elseBranch == null ? otherwise : label();
        emit(TacInstruction.of(Op.IF_FALSE, null, null, condition, otherwise));
        stmt.thenBranch.accept(this);
        if (stmt.elseBranch != null) {
            emit(TacInstruction.of(Op.GOTO, null, null, end));
            emit(TacInstruction.of(Op.LABEL, otherwise, null));
            stmt.elseBranch.accept(this);
            emit(TacInstruction.of(Op.LABEL, end, null));
        } else {
            emit(TacInstruction.of(Op.LABEL, otherwise, null));
        }
        return null;
    }

    @Override public Void visitWhileStmt(Stmt.While stmt) {
        Operand start = label();
        Operand end = label();
        emit(TacInstruction.of(Op.LABEL, start, null));
        Operand condition = stmt.condition.accept(this);
        emit(TacInstruction.of(Op.IF_FALSE, null, null, condition, end));
        stmt.body.accept(this);
        emit(TacInstruction.of(Op.GOTO, null, null, start));
        emit(TacInstruction.of(Op.LABEL, end, null));
        return null;
    }

    @Override public Void visitSwitchStmt(Stmt.Switch stmt) {
        Operand subject = snapshot(stmt.subject.accept(this));
        Operand end = label();
        List<Operand> bodies = new ArrayList<>();
        List<Operand> nextCases = new ArrayList<>();
        for (int i = 0; i < stmt.caseValues.size(); i++) {
            bodies.add(label());
            nextCases.add(label());
            Operand value = stmt.caseValues.get(i).accept(this);
            Operand equal = temporary();
            emit(TacInstruction.of(Op.BINARY, equal, "==", subject, value));
            emit(TacInstruction.of(Op.IF_TRUE, null, null, equal, bodies.get(i)));
            emit(TacInstruction.of(Op.GOTO, null, null, nextCases.get(i)));
            emit(TacInstruction.of(Op.LABEL, bodies.get(i), null));
            scoped(stmt.caseBodies.get(i));
            emit(TacInstruction.of(Op.GOTO, null, null, end));
            emit(TacInstruction.of(Op.LABEL, nextCases.get(i), null));
        }
        if (stmt.defaultBranch != null) scoped(stmt.defaultBranch);
        emit(TacInstruction.of(Op.LABEL, end, null));
        return null;
    }

    @Override public Void visitFunctionStmt(Stmt.Function stmt) {
        Operand destination;
        if (scopes.isEmpty()) {
            destination = Operand.variable(stmt.name.name());
        } else {
            destination = Operand.variable(stmt.name.name() + "$" + (++bindingNumber));
            scopes.peek().bindings.put(stmt.name.name(), destination);
            move(destination, Operand.constant(null)); // Reserva a celula para recursao local.
        }
        move(destination, closure(stmt.name.name(), stmt.params, stmt.body));
        return null;
    }

    @Override public Void visitReturnStmt(Stmt.Return stmt) {
        Operand value = stmt.value == null ? Operand.constant(null) : stmt.value.accept(this);
        emit(TacInstruction.of(Op.RETURN, null, null, value));
        return null;
    }
}
