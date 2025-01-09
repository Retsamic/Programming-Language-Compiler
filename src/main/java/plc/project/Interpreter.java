package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.math.RoundingMode;


public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope;

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        try {
            Environment.Function mainFunction = scope.lookupFunction("main", 0);
            return mainFunction.invoke(Collections.emptyList());
        } catch (RuntimeException e) {
            throw new RuntimeException("Main function not found.");
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        Environment.PlcObject value = Environment.NIL;
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        }
        scope.defineVariable(ast.getName(), ast.getConstant(), value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        Scope definingScope = scope;

        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope previousScope = scope;
            try {
                scope = new Scope(definingScope);

                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), false, args.get(i));
                }

                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
                return Environment.NIL;
            } catch (Return returnValue) {
                return returnValue.value;
            } finally {
                scope = previousScope;
            }
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Environment.PlcObject value = Environment.NIL;
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        }
        scope.defineVariable(ast.getName(), false, value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Receiver is not assignable.");
        }

        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        Environment.PlcObject value = visit(ast.getValue());

        if (access.getReceiver().isPresent()) {
            Environment.PlcObject receiverObject = visit(access.getReceiver().get());
            Environment.Variable field = receiverObject.getField(access.getName());

            if (field.getConstant() && field.getValue() != Environment.NIL) {
                throw new RuntimeException("Cannot assign to a constant field.");
            }

            field.setValue(value);
        } else {
            Environment.Variable variable = scope.lookupVariable(access.getName());

            if (variable.getConstant() && variable.getValue() != Environment.NIL) {
                throw new RuntimeException("Cannot assign to a constant variable.");
            }

            variable.setValue(value);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Environment.PlcObject condition = visit(ast.getCondition());
        Boolean conditionValue = requireType(Boolean.class, condition);

        Scope previousScope = scope;
        try {
            scope = new Scope(previousScope);
            if (conditionValue) {
                for (Ast.Statement stmt : ast.getThenStatements()) {
                    visit(stmt);
                }
            } else {
                for (Ast.Statement stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            }
        } finally {
            scope = previousScope;
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        Scope previousScope = scope;
        try {
            scope = new Scope(previousScope);

            visit(ast.getInitialization());

            while (true) {
                Environment.PlcObject condition = visit(ast.getCondition());
                Boolean conditionValue = requireType(Boolean.class, condition);

                if (!conditionValue) {
                    break;
                }

                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }

                visit(ast.getIncrement());
            }
        } finally {
            scope = previousScope;
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        Scope previousScope = scope;
        try {
            scope = new Scope(previousScope);

            while (true) {
                Environment.PlcObject condition = visit(ast.getCondition());
                Boolean conditionValue = requireType(Boolean.class, condition);

                if (!conditionValue) {
                    break;
                }

                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
            }
        } finally {
            scope = previousScope;
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        Environment.PlcObject value = visit(ast.getValue());
        throw new Return(value);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        Object literal = ast.getLiteral();
        if (literal == null) {
            return Environment.NIL;
        } else {
            return Environment.create(literal);
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();
        switch (operator) {
            case "&&": {
                Environment.PlcObject left = visit(ast.getLeft());
                Boolean leftValue = requireType(Boolean.class, left);
                if (!leftValue) {
                    return Environment.create(false);
                }
                Environment.PlcObject right = visit(ast.getRight());
                Boolean rightValue = requireType(Boolean.class, right);
                return Environment.create(leftValue && rightValue);
            }
            case "||": {
                Environment.PlcObject left = visit(ast.getLeft());
                Boolean leftValue = requireType(Boolean.class, left);
                if (leftValue) {
                    return Environment.create(true);
                }
                Environment.PlcObject right = visit(ast.getRight());
                Boolean rightValue = requireType(Boolean.class, right);
                return Environment.create(leftValue || rightValue);
            }
            case "<":
            case "<=":
            case ">":
            case ">=": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                Comparable leftValue = requireType(Comparable.class, left);
                Comparable rightValue = requireType(leftValue.getClass(), right);

                int comparison = leftValue.compareTo(rightValue);
                boolean result;
                switch (operator) {
                    case "<":
                        result = comparison < 0;
                        break;
                    case "<=":
                        result = comparison <= 0;
                        break;
                    case ">":
                        result = comparison > 0;
                        break;
                    case ">=":
                        result = comparison >= 0;
                        break;
                    default:
                        throw new RuntimeException("Unknown operator: " + operator);
                }
                return Environment.create(result);
            }
            case "==":
            case "!=": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                boolean isEqual = Objects.equals(left.getValue(), right.getValue());
                if (operator.equals("!=")) {
                    isEqual = !isEqual;
                }
                return Environment.create(isEqual);
            }
            case "+": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    String leftStr = left.getValue().toString();
                    String rightStr = right.getValue().toString();
                    return Environment.create(leftStr + rightStr);
                } else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger leftVal = requireType(BigInteger.class, left);
                    BigInteger rightVal = requireType(BigInteger.class, right);
                    return Environment.create(leftVal.add(rightVal));
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    BigDecimal leftVal = requireType(BigDecimal.class, left);
                    BigDecimal rightVal = requireType(BigDecimal.class, right);
                    return Environment.create(leftVal.add(rightVal));
                } else {
                    throw new RuntimeException("Invalid types for addition.");
                }
            }
            case "-":
            case "*":
            case "/": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger leftVal = requireType(BigInteger.class, left);
                    BigInteger rightVal = requireType(BigInteger.class, right);
                    switch (operator) {
                        case "-":
                            return Environment.create(leftVal.subtract(rightVal));
                        case "*":
                            return Environment.create(leftVal.multiply(rightVal));
                        case "/":
                            if (rightVal.equals(BigInteger.ZERO)) {
                                throw new RuntimeException("Division by zero.");
                            }
                            return Environment.create(leftVal.divide(rightVal));
                    }
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    BigDecimal leftVal = requireType(BigDecimal.class, left);
                    BigDecimal rightVal = requireType(BigDecimal.class, right);
                    switch (operator) {
                        case "-":
                            return Environment.create(leftVal.subtract(rightVal));
                        case "*":
                            return Environment.create(leftVal.multiply(rightVal));
                        case "/":
                            if (rightVal.compareTo(BigDecimal.ZERO) == 0) {
                                throw new RuntimeException("Division by zero.");
                            }
                            return Environment.create(leftVal.divide(rightVal, RoundingMode.HALF_EVEN));
                    }
                } else {
                    throw new RuntimeException("Invalid types for operator " + operator);
                }
            }
            default:
                throw new RuntimeException("Unknown operator: " + operator);
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiverObject = visit(ast.getReceiver().get());
            return receiverObject.getField(ast.getName()).getValue();
        } else {
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for (Ast.Expression argExpr : ast.getArguments()) {
            arguments.add(visit(argExpr));
        }

        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiverObject = visit(ast.getReceiver().get());
            return receiverObject.callMethod(ast.getName(), arguments);
        } else {
            Environment.Function function = scope.lookupFunction(ast.getName(), arguments.size());
            return function.invoke(arguments);
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (object.getValue() == Environment.NIL.getValue()) {
            throw new RuntimeException("Expected type " + type.getName() + ", received NIL.");
        }
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
