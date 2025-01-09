package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Analyzer implements Ast.Visitor<Void> {

    private Scope analyzerScope;
    public final Scope scope;
    private Environment.Type expectedReturnType;

    public Analyzer(Scope parent) {
        analyzerScope = new Scope(parent);
        scope = analyzerScope;
        analyzerScope.defineFunction("print", "System.out.println",
                Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    @Override
    public Void visit(Ast.Source ast) {
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }
        Environment.Function mainFunction = analyzerScope.lookupFunction("main", 0);
        if (mainFunction == null) {
            throw new ParseException("Main function with 0 parameters not found.", -1);
        }
        if (!mainFunction.getReturnType().equals(Environment.Type.INTEGER)) {
            throw new ParseException("Main function must have return type Integer.", -1);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        Environment.Type type = Environment.getType(ast.getTypeName());
        if (ast.getConstant() && !ast.getValue().isPresent()) {
            throw new ParseException("Constant field must have an initial value.", -1);
        }
        if (ast.getValue().isPresent()) {
            Environment.Type valueType = ast.getValue().get().getType();
            requireAssignable(type, valueType);
        }
        boolean constant = ast.getConstant();
        Environment.Variable variable = analyzerScope.defineVariable(
                ast.getName(),
                ast.getName(),
                type,
                constant,
                Environment.NIL
        );
        ast.setVariable(variable);
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        List<Environment.Type> parameterTypes = new ArrayList<>();
        for (String typeName : ast.getParameterTypeNames()) {
            Environment.Type paramType = Environment.getType(typeName);
            parameterTypes.add(paramType);
        }
        Environment.Type returnType = ast.getReturnTypeName().isPresent()
                ? Environment.getType(ast.getReturnTypeName().get())
                : Environment.Type.NIL;
        Environment.Function function = analyzerScope.defineFunction(
                ast.getName(),
                ast.getName(),
                parameterTypes,
                returnType,
                args -> Environment.NIL
        );
        ast.setFunction(function);
        Environment.Type previousReturnType = expectedReturnType;
        expectedReturnType = returnType;
        analyzerScope = new Scope(analyzerScope);
        List<String> parameters = ast.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            String paramName = parameters.get(i);
            Environment.Type paramType = parameterTypes.get(i);
            boolean constant = false;
            analyzerScope.defineVariable(
                    paramName,
                    paramName,
                    paramType,
                    constant,
                    Environment.NIL
            );
        }
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        analyzerScope = analyzerScope.getParent();
        expectedReturnType = previousReturnType;
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new ParseException("Expression statements must be function calls.", -1);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        Environment.Type type;
        if (ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        } else if (ast.getValue().isPresent()) {
            type = ast.getValue().get().getType();
        } else {
            throw new ParseException("Declaration must have a type or an initial value.", -1);
        }
        if (ast.getValue().isPresent()) {
            Environment.Type valueType = ast.getValue().get().getType();
            requireAssignable(type, valueType);
        }
        boolean constant = false;
        Environment.Variable variable = analyzerScope.defineVariable(
                ast.getName(),
                ast.getName(),
                type,
                constant,
                Environment.NIL
        );
        ast.setVariable(variable);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        visit(ast.getReceiver());
        visit(ast.getValue());
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new ParseException("Receiver must be an access expression.", -1);
        }
        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable variable = access.getVariable();
        if (variable.getConstant()) {
            throw new ParseException("Cannot assign to a constant variable.", -1);
        }
        requireAssignable(variable.getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new ParseException("If condition must be of type Boolean.", -1);
        }
        if (ast.getThenStatements().isEmpty()) {
            throw new ParseException("Then statements cannot be empty.", -1);
        }
        analyzerScope = new Scope(analyzerScope);
        for (Ast.Statement statement : ast.getThenStatements()) {
            visit(statement);
        }
        analyzerScope = analyzerScope.getParent();
        if (!ast.getElseStatements().isEmpty()) {
            analyzerScope = new Scope(analyzerScope);
            for (Ast.Statement statement : ast.getElseStatements()) {
                visit(statement);
            }
            analyzerScope = analyzerScope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        if (ast.getInitialization() != null) {
            visit(ast.getInitialization());
        }
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new ParseException("For loop condition must be of type Boolean.", -1);
        }
        if (ast.getIncrement() != null) {
            visit(ast.getIncrement());
        }
        if (ast.getStatements().isEmpty()) {
            throw new ParseException("For loop body cannot be empty.", -1);
        }
        analyzerScope = new Scope(analyzerScope);
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        analyzerScope = analyzerScope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new ParseException("While loop condition must be of type Boolean.", -1);
        }
        if (ast.getStatements().isEmpty()) {
            throw new ParseException("While loop body cannot be empty.", -1);
        }
        analyzerScope = new Scope(analyzerScope);
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        analyzerScope = analyzerScope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());
        requireAssignable(expectedReturnType, ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object value = ast.getLiteral();
        if (value == null) {
            ast.setType(Environment.Type.NIL);
        } else if (value instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (value instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (value instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (value instanceof BigInteger) {
            ast.setType(Environment.Type.INTEGER);
        } else if (value instanceof BigDecimal) {
            ast.setType(Environment.Type.DECIMAL);
        } else {
            throw new ParseException("Unknown literal type: " + value.getClass().getSimpleName(), -1);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        String operator = ast.getOperator();
        Environment.Type leftType = ast.getLeft().getType();
        Environment.Type rightType = ast.getRight().getType();
        switch (operator) {
            case "&&":
            case "||":
                if (!leftType.equals(Environment.Type.BOOLEAN) ||
                        !rightType.equals(Environment.Type.BOOLEAN)) {
                    throw new ParseException("Both operands of logical operators must be Boolean.", -1);
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
                if (!isComparable(leftType) || !leftType.equals(rightType)) {
                    throw new ParseException("Both operands of comparison operators must be Comparable and of the same type.", -1);
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (leftType.equals(Environment.Type.STRING) || rightType.equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else if ((leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL)) &&
                        leftType.equals(rightType)) {
                    ast.setType(leftType);
                } else {
                    throw new ParseException("Invalid operands for '+'.", -1);
                }
                break;
            case "-":
            case "*":
            case "/":
                if ((leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL)) &&
                        leftType.equals(rightType)) {
                    ast.setType(leftType);
                } else {
                    throw new ParseException("Invalid operands for '" + operator + "'.", -1);
                }
                break;
            default:
                throw new ParseException("Unknown binary operator: " + operator, -1);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            throw new UnsupportedOperationException("Field access not supported.");
        } else {
            Environment.Variable variable = analyzerScope.lookupVariable(ast.getName());
            ast.setVariable(variable);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        for (Ast.Expression argument : ast.getArguments()) {
            visit(argument);
        }
        Environment.Function function;
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Type receiverType = ast.getReceiver().get().getType();
            function = receiverType.getFunction(ast.getName(), ast.getArguments().size() + 1);
            ast.setFunction(function);
            List<Environment.Type> parameterTypes = function.getParameterTypes();
            requireAssignable(parameterTypes.get(0), receiverType);
            for (int i = 0; i < ast.getArguments().size(); i++) {
                requireAssignable(parameterTypes.get(i + 1), ast.getArguments().get(i).getType());
            }
        } else {
            function = analyzerScope.lookupFunction(ast.getName(), ast.getArguments().size());
            ast.setFunction(function);
            List<Environment.Type> parameterTypes = function.getParameterTypes();
            for (int i = 0; i < ast.getArguments().size(); i++) {
                requireAssignable(parameterTypes.get(i), ast.getArguments().get(i).getType());
            }
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.equals(Environment.Type.ANY)) {
            return;
        } else if (target.equals(Environment.Type.COMPARABLE)) {
            if (isComparable(type)) {
                return;
            } else {
                throw new ParseException("Type " + type + " is not assignable to Comparable.", -1);
            }
        } else if (target.equals(type)) {
            return;
        } else {
            throw new ParseException("Type " + type + " is not assignable to " + target + ".", -1);
        }
    }

    private static boolean isComparable(Environment.Type type) {
        return type.equals(Environment.Type.INTEGER) ||
                type.equals(Environment.Type.DECIMAL) ||
                type.equals(Environment.Type.CHARACTER) ||
                type.equals(Environment.Type.STRING);
    }
}

class ParseException extends RuntimeException {
    private final int index;

    public ParseException(String message, int index) {
        super(message + " at index " + index);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
