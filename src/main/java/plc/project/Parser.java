package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        while (peek("LET")) {
            fields.add(parseField());
        }

        while (peek("DEF")) {
            methods.add(parseMethod());
        }

        return new Ast.Source(fields, methods);
    }

    public Ast.Field parseField() throws ParseException {
        consume("LET", "Expected 'LET' at the beginning of a field declaration.");

        boolean isConst = match("CONST");

        Token identifierToken = consume(Token.Type.IDENTIFIER, "Expected identifier in field declaration.");
        String identifier = identifierToken.getLiteral();

        if (match(":")) {
            consume(Token.Type.IDENTIFIER, "Expected type name after ':'.");
        }

        Ast.Expression initializer = null;
        if (match("=")) {
            initializer = parseExpression();
        }

        consume(";", "Expected ';' after field declaration.");

        return new Ast.Field(identifier, isConst, Optional.ofNullable(initializer));
    }

    public Ast.Method parseMethod() throws ParseException {
        consume("DEF", "Expected 'DEF' at the beginning of a method declaration.");

        Token nameToken = consume(Token.Type.IDENTIFIER, "Expected method name after 'DEF'.");
        String name = nameToken.getLiteral();

        consume("(", "Expected '(' after method name.");

        List<String> parameters = new ArrayList<>();
        if (!peek(")")) {
            do {
                Token paramToken = consume(Token.Type.IDENTIFIER, "Expected parameter name.");
                parameters.add(paramToken.getLiteral());

                if (match(":")) {
                    consume(Token.Type.IDENTIFIER, "Expected parameter type name after ':'.");
                }
            } while (match(","));
        }

        consume(")", "Expected ')' after parameters.");

        if (match(":")) {
            consume(Token.Type.IDENTIFIER, "Expected return type name after ':'.");
        }

        consume("DO", "Expected 'DO' after method parameters (and optional return type).");

        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }

        consume("END", "Expected 'END' after method body.");

        return new Ast.Method(name, parameters, statements);
    }

    public Ast.Statement parseStatement() throws ParseException {
        if (match("LET")) {
            return parseDeclarationStatement();
        } else if (match("IF")) {
            return parseIfStatement();
        } else if (match("FOR")) {
            return parseForStatement();
        } else if (match("WHILE")) {
            return parseWhileStatement();
        } else if (match("RETURN")) {
            return parseReturnStatement();
        } else {
            Ast.Expression expression = parseExpression();
            if (match("=")) {
                Ast.Expression value = parseExpression();
                consume(";", "Expected ';' after assignment.");
                return new Ast.Statement.Assignment(expression, value);
            } else {
                consume(";", "Expected ';' after expression.");
                return new Ast.Statement.Expression(expression);
            }
        }
    }

    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        Token identifierToken = consume(Token.Type.IDENTIFIER, "Expected identifier in declaration.");
        String identifier = identifierToken.getLiteral();

        Optional<String> typeName = Optional.empty();
        if (match(":")) {
            Token typeToken = consume(Token.Type.IDENTIFIER, "Expected type name after ':'.");
            typeName = Optional.of(typeToken.getLiteral());
        }

        Ast.Expression initializer = null;
        if (match("=")) {
            initializer = parseExpression();
        }

        consume(";", "Expected ';' after variable declaration.");

        return new Ast.Statement.Declaration(identifier, typeName, Optional.ofNullable(initializer));
    }

    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression condition = parseExpression();

        consume("DO", "Expected 'DO' after if condition.");

        List<Ast.Statement> thenStatements = new ArrayList<>();
        while (!peek("ELSE") && !peek("END")) {
            thenStatements.add(parseStatement());
        }

        List<Ast.Statement> elseStatements = new ArrayList<>();
        if (match("ELSE")) {
            while (!peek("END")) {
                elseStatements.add(parseStatement());
            }
        }

        consume("END", "Expected 'END' after if statement.");

        return new Ast.Statement.If(condition, thenStatements, elseStatements);
    }

    public Ast.Statement.For parseForStatement() throws ParseException {
        consume("(", "Expected '(' after 'FOR'.");

        Ast.Statement initializer = null;
        if (!peek(";")) {
            if (peek("LET")) {
                initializer = parseDeclarationStatement();
            } else {
                Ast.Expression receiver = parseExpression();
                consume("=", "Expected '=' after for-loop initializer.");
                Ast.Expression value = parseExpression();
                consume(";", "Expected ';' after for-loop initializer.");
                initializer = new Ast.Statement.Assignment(receiver, value);
            }
        } else {
            consume(";", "Expected ';' after for-loop initializer.");
        }

        Ast.Expression condition = null;
        if (!peek(";")) {
            condition = parseExpression();
        }
        consume(";", "Expected ';' after for-loop condition.");

        Ast.Statement.Assignment updater = null;
        if (!peek(")")) {
            Ast.Expression updaterExpr = parseExpression();
            if ((updaterExpr instanceof Ast.Expression.Access) || (updaterExpr instanceof Ast.Expression.Function)) {
                if (match("=")) {
                    Ast.Expression value = parseExpression();
                    updater = new Ast.Statement.Assignment(updaterExpr, value);
                } else {
                    throw new ParseException("Expected '=' in for-loop updater.",
                            tokens.has(0) ? tokens.get(0).getIndex() : -1);
                }
            } else {
                throw new ParseException("Invalid for-loop updater.",
                        tokens.has(0) ? tokens.get(0).getIndex() : -1);
            }
        }

        consume(")", "Expected ')' after for-loop components.");


        List<Ast.Statement> bodyStatements = new ArrayList<>();
        while (!peek("END")) {
            bodyStatements.add(parseStatement());
        }

        consume("END", "Expected 'END' after for-loop body.");

        return new Ast.Statement.For(initializer, condition, updater, bodyStatements);
    }

    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression condition = parseExpression();

        consume("DO", "Expected 'DO' after while condition.");

        List<Ast.Statement> bodyStatements = new ArrayList<>();
        while (!peek("END")) {
            bodyStatements.add(parseStatement());
        }

        consume("END", "Expected 'END' after while-loop body.");

        return new Ast.Statement.While(condition, bodyStatements);
    }

    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression value = parseExpression();

        consume(";", "Expected ';' after return value.");

        return new Ast.Statement.Return(value);
    }

    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression left = parseComparisonExpression();
        while (match("&&") || match("||")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseComparisonExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression left = parseAdditiveExpression();
        while (match("<") || match("<=") || match(">") || match(">=") || match("==") || match("!=")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseAdditiveExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression left = parseMultiplicativeExpression();
        while (match("+") || match("-")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseMultiplicativeExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression left = parseSecondaryExpression();
        while (match("*") || match("/")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseSecondaryExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    public Ast.Expression parseSecondaryExpression() throws ParseException {
        Ast.Expression expression = parsePrimaryExpression();
        while (true) {
            if (match(".")) {
                if (match(Token.Type.IDENTIFIER)) {
                    String name = tokens.get(-1).getLiteral();
                    if (match("(")) {
                        List<Ast.Expression> arguments = new ArrayList<>();
                        if (!peek(")")) {
                            do {
                                arguments.add(parseExpression());
                            } while (match(","));
                        }
                        consume(")", "Expected ')' after function arguments.");
                        expression = new Ast.Expression.Function(Optional.of(expression), name, arguments);
                    } else {
                        expression = new Ast.Expression.Access(Optional.of(expression), name);
                    }
                } else {
                    throw new ParseException("Expected identifier after '.'.", tokens.has(0) ? tokens.get(0).getIndex() : -1);
                }
            } else {
                break;
            }
        }
        return expression;
    }

    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (match("NIL")) {
            return new Ast.Expression.Literal(null);
        } else if (match("TRUE")) {
            return new Ast.Expression.Literal(Boolean.TRUE);
        } else if (match("FALSE")) {
            return new Ast.Expression.Literal(Boolean.FALSE);
        } else if (match(Token.Type.INTEGER)) {
            String literal = tokens.get(-1).getLiteral();
            try {
                BigInteger value = new BigInteger(literal);
                return new Ast.Expression.Literal(value);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid integer literal.", tokens.get(-1).getIndex());
            }
        } else if (match(Token.Type.DECIMAL)) {
            String literal = tokens.get(-1).getLiteral();
            try {
                BigDecimal value = new BigDecimal(literal);
                return new Ast.Expression.Literal(value);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid decimal literal.", tokens.get(-1).getIndex());
            }
        } else if (match(Token.Type.CHARACTER)) {
            String literal = tokens.get(-1).getLiteral();
            char value = parseCharacterLiteral(literal);
            return new Ast.Expression.Literal(value);
        } else if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            String value = parseStringLiteral(literal);
            return new Ast.Expression.Literal(value);
        } else if (match("(")) {
            Ast.Expression expression = parseExpression();
            consume(")", "Expected ')' after expression.");
            return new Ast.Expression.Group(expression);
        } else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            if (match("(")) {
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!peek(")")) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(","));
                }
                consume(")", "Expected ')' after function arguments.");
                return new Ast.Expression.Function(Optional.empty(), name, arguments);
            } else {
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        } else {
            throw new ParseException("Expected primary expression.", tokens.has(0) ? tokens.get(0).getIndex() : -1);
        }
    }

    private char parseCharacterLiteral(String literal) throws ParseException {
        String content = literal.substring(1, literal.length() - 1);
        if (content.length() == 1) {
            return content.charAt(0);
        } else if (content.startsWith("\\")) {
            if (content.length() != 2) {
                throw new ParseException("Invalid character literal.", tokens.get(-1).getIndex());
            }
            char escape = content.charAt(1);
            return switch (escape) {
                case 'b' -> '\b';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '\'' -> '\'';
                case '"' -> '\"';
                case '\\' -> '\\';
                default ->
                        throw new ParseException("Invalid escape sequence in character literal.", tokens.get(-1).getIndex());
            };
        } else {
            throw new ParseException("Invalid character literal.", tokens.get(-1).getIndex());
        }
    }

    private String parseStringLiteral(String literal) {
        String content = literal.substring(1, literal.length() - 1);
        content = content.replace("\\b", "\b")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        return content;
    }

    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            }
            Object pattern = patterns[i];
            Token token = tokens.get(i);
            if (pattern instanceof Token.Type) {
                if (token.getType() != pattern) {
                    return false;
                }
            } else if (pattern instanceof String) {
                if (!token.getLiteral().equals(pattern)) {
                    return false;
                }
            } else {
                throw new IllegalArgumentException("Invalid pattern object: " + pattern);
            }
        }
        return true;
    }

    private boolean match(Object... patterns) {
        boolean matches = peek(patterns);
        if (matches) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return matches;
    }

    private Token consume(Object expected, String errorMessage) throws ParseException {
        if (peek(expected)) {
            Token token = tokens.get(0);
            tokens.advance();
            return token;
        }
        throw new ParseException(errorMessage, tokens.has(0) ? tokens.get(0).getIndex() : -1);
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        public void advance() {
            if (index < tokens.size()) {
                index++;
            }
        }

    }

}
