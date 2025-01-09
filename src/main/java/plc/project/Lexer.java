package plc.project;

import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private final CharStream input;
    private static final String[] OPERATORS = {
            "&&", "||", "==", "!=", "<=", ">=",
            "+", "-", "*", "/", "%", "<", ">", "=", "!", "^", "~", "@",
            "(", ")", "{", "}", "[", "]", ",", ";", ".", ":"
    };

    public Lexer(String input) {
        this.input = new CharStream(input);
    }

    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        while (input.has(0)) {
            if (peek("[ \b\n\r\t]")) {
                input.advance();
                input.skip();
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    public Token lexToken() {
        if (peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (peek("[+-]?")) {
            if (peek("[+-]?", "0")) {
                return lexNumber();
            } else if (peek("[+-]?", "[1-9]")) {
                return lexNumber();
            } else {
                return lexOperator();
            }
        } else if (peek("0") || peek("[1-9]")) {
            return lexNumber();
        } else if (peek("'")) {
            return lexCharacter();
        } else if (peek("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }
    }

    public Token lexIdentifier() {
        match("[A-Za-z_]");
        while (match("[A-Za-z0-9_-]")) {
        }
        return input.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        match("[+-]?");
        if (match("0")) {
            if (peek("[0-9]")) {
                throw new ParseException("Invalid integer with leading zero", input.index);
            }
            if (peek("\\.")) {
                return lexDecimal();
            } else {
                return input.emit(Token.Type.INTEGER);
            }
        } else if (match("[1-9]")) {
            while (match("[0-9]")) {}
            if (peek("\\.")) {
                return lexDecimal();
            } else {
                return input.emit(Token.Type.INTEGER);
            }
        } else {
            throw new ParseException("Invalid number", input.index);
        }
    }

    private Token lexDecimal() {
        match("\\.");
        if (!match("[0-9]")) {
            throw new ParseException("Invalid decimal number", input.index);
        }
        while (match("[0-9]")) {}
        return input.emit(Token.Type.DECIMAL);
    }

    public Token lexCharacter() {
        match("'");
        if (match("[^'\\\\\\n\\r]")) {
        } else if (match("\\\\")) {
            if (!match("[bnrt'\"\\\\]")) {
                throw new ParseException("Invalid escape sequence in character literal", input.index);
            }
        } else {
            throw new ParseException("Invalid character literal", input.index);
        }
        if (!match("'")) {
            throw new ParseException("Unterminated character literal", input.index);
        }
        return input.emit(Token.Type.CHARACTER);
    }

    public Token lexString() {
        match("\"");
        while (!peek("\"")) {
            if (!input.has(0)) {
                throw new ParseException("Unterminated string literal", input.index);
            }
            if (match("[^\"\\\\\\n\\r]")) {
            } else if (match("\\\\")) {
                if (!match("[bnrt'\"\\\\]")) {
                    throw new ParseException("Invalid escape sequence in string literal", input.index);
                }
            } else {
                throw new ParseException("Invalid string literal", input.index);
            }
        }
        match("\"");
        return input.emit(Token.Type.STRING);
    }

    public Token lexOperator() {
        for (String op : OPERATORS) {
            if (matchExact(op)) {
                return input.emit(Token.Type.OPERATOR);
            }
        }
        throw new ParseException("Invalid operator", input.index);
    }

    public boolean peek(String... patterns) {
        int offset = 0;
        for (String pattern : patterns) {
            if (!input.has(offset)) return false;
            String s = String.valueOf(input.get(offset));
            if (!s.matches(pattern)) {
                return false;
            }
            offset++;
        }
        return true;
    }

    public boolean match(String... patterns) {
        if (!peek(patterns)) return false;
        for (String ignored : patterns) {
            input.advance();
        }
        return true;
    }

    public boolean peekExact(String pattern) {
        if (!input.has(pattern.length() - 1)) {
            return false;
        }
        String s = input.input.substring(input.index, input.index + pattern.length());
        return s.equals(pattern);
    }

    public boolean matchExact(String pattern) {
        if (!peekExact(pattern)) return false;
        for (int i = 0; i < pattern.length(); i++) {
            input.advance();
        }
        return true;
    }

    public static final class CharStream {
        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }
    }
}
