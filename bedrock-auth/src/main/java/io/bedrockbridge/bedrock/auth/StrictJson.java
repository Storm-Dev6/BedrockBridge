package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser with strict syntax, depth, and input-size limits. */
public final class StrictJson {
    private static final int MAXIMUM_BYTES = 1 << 20;
    private static final int MAXIMUM_DEPTH = 32;

    private StrictJson() {}

    /** Parses a JSON object and rejects duplicate keys and trailing input. */
    public static Map<String, Object> parseObject(String input) {
        if (input == null || input.length() > MAXIMUM_BYTES) {
            throw new BedrockValidationException("JSON exceeds size limit");
        }
        Parser parser = new Parser(input);
        Object value = parser.value(0);
        parser.whitespace();
        if (parser.position != input.length() || !(value instanceof Map<?, ?> map)) {
            throw new BedrockValidationException("Expected one complete JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return Map.copyOf(typed);
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) { this.input = input; }

        private Object value(int depth) {
            if (depth > MAXIMUM_DEPTH) fail("JSON nesting limit exceeded");
            whitespace();
            if (position >= input.length()) fail("Unexpected end of JSON");
            return switch (input.charAt(position)) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> nullValue();
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) return result;
            while (true) {
                whitespace();
                if (position >= input.length() || input.charAt(position) != '"') fail("Expected object key");
                String key = string();
                whitespace();
                if (!consume(':')) fail("Expected colon");
                if (result.containsKey(key)) fail("Duplicate JSON key");
                result.put(key, value(depth));
                whitespace();
                if (consume('}')) return result;
                if (!consume(',')) fail("Expected comma");
            }
        }

        private List<Object> array(int depth) {
            position++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (consume(']')) return List.copyOf(result);
            while (true) {
                result.add(value(depth));
                whitespace();
                if (consume(']')) return List.copyOf(result);
                if (!consume(',')) fail("Expected comma");
            }
        }

        private String string() {
            position++;
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') return result.toString();
                if (current < 0x20) fail("Control character in string");
                if (current != '\\') { result.append(current); continue; }
                if (position >= input.length()) fail("Truncated escape");
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicode());
                    default -> fail("Invalid string escape");
                }
            }
            fail("Unterminated string");
            return "";
        }

        private char unicode() {
            if (position + 4 > input.length()) fail("Truncated unicode escape");
            try {
                char value = (char) Integer.parseInt(input.substring(position, position + 4), 16);
                position += 4;
                return value;
            } catch (NumberFormatException invalid) {
                fail("Invalid unicode escape");
                return 0;
            }
        }

        private Object number() {
            int start = position;
            consume('-');
            if (position >= input.length()) fail("Truncated number");
            if (consume('0')) {
                if (position < input.length() && Character.isDigit(input.charAt(position))) {
                    fail("Leading zero in number");
                }
            } else {
                int digits = position;
                while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
                if (digits == position) fail("Expected number");
            }
            boolean fractional = false;
            if (consume('.')) {
                fractional = true;
                int digits = position;
                while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
                if (digits == position) fail("Missing fraction digits");
            }
            if (position < input.length() && "eE".indexOf(input.charAt(position)) >= 0) {
                fractional = true;
                position++;
                if (position < input.length() && "+-".indexOf(input.charAt(position)) >= 0) position++;
                int digits = position;
                while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
                if (digits == position) fail("Missing exponent digits");
            }
            if (fractional) {
                try {
                    double value = Double.parseDouble(input.substring(start, position));
                    if (!Double.isFinite(value)) fail("Non-finite number");
                    return value;
                } catch (NumberFormatException invalid) {
                    fail("Invalid number");
                }
            }
            try { return Long.parseLong(input.substring(start, position)); }
            catch (NumberFormatException invalid) { fail("Invalid number"); return 0; }
        }

        private Object literal(String text, Object value) {
            if (!input.startsWith(text, position)) fail("Invalid literal");
            position += text.length();
            return value;
        }

        private Object nullValue() {
            fail("Null JSON values are not accepted in authentication data");
            return null;
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private void whitespace() {
            while (position < input.length() && " \t\r\n".indexOf(input.charAt(position)) >= 0) position++;
        }

        private static void fail(String message) { throw new BedrockValidationException(message); }
    }
}
