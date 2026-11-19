package ru.plusrep.fabrica;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Лишние символы после JSON.");
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        writeValue(output, value);
        return output.toString();
    }

    private static void writeValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            writeString(output, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                writeString(output, String.valueOf(entry.getKey()));
                output.append(':');
                writeValue(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) output.append(',');
                first = false;
                writeValue(output, item);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Неподдерживаемый тип JSON: " + value.getClass().getName());
        }
    }

    private static void writeString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Object readValue() {
            skipWhitespace();
            if (isAtEnd()) throw error("Ожидалось значение JSON.");
            return switch (source.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            index++;
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) return object;
            while (true) {
                skipWhitespace();
                if (isAtEnd() || source.charAt(index) != '"') throw error("Ожидался ключ объекта.");
                String key = readString();
                skipWhitespace();
                require(':');
                object.put(key, readValue());
                skipWhitespace();
                if (take('}')) return object;
                require(',');
            }
        }

        private List<Object> readArray() {
            index++;
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (take(']')) return array;
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (take(']')) return array;
                require(',');
            }
        }

        private String readString() {
            require('"');
            StringBuilder value = new StringBuilder();
            while (!isAtEnd()) {
                char character = source.charAt(index++);
                if (character == '"') return value.toString();
                if (character != '\\') {
                    if (character < 0x20) throw error("Недопустимый символ в строке.");
                    value.append(character);
                    continue;
                }
                if (isAtEnd()) throw error("Незавершённая escape-последовательность.");
                char escape = source.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw error("Неизвестная escape-последовательность.");
                }
            }
            throw error("Незакрытая строка.");
        }

        private char readUnicodeEscape() {
            if (index + 4 > source.length()) throw error("Незавершённая Unicode-последовательность.");
            try {
                char result = (char) Integer.parseInt(source.substring(index, index + 4), 16);
                index += 4;
                return result;
            } catch (NumberFormatException exception) {
                throw error("Некорректная Unicode-последовательность.");
            }
        }

        private Object readNumber() {
            int start = index;
            take('-');
            if (!take('0')) {
                requireDigit();
                while (isDigit()) index++;
            }
            boolean decimal = false;
            if (take('.')) {
                decimal = true;
                requireDigit();
                while (isDigit()) index++;
            }
            if (take('e') || take('E')) {
                decimal = true;
                if (!take('+')) take('-');
                requireDigit();
                while (isDigit()) index++;
            }
            String token = source.substring(start, index);
            try {
                return decimal ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException exception) {
                throw error("Некорректное число.");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!source.startsWith(literal, index)) throw error("Некорректное значение.");
            index += literal.length();
            return value;
        }

        private void requireDigit() {
            if (!isDigit()) throw error("Ожидалась цифра.");
        }

        private boolean isDigit() {
            return !isAtEnd() && source.charAt(index) >= '0' && source.charAt(index) <= '9';
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (!isAtEnd() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw error("Ожидался символ '" + expected + "'.");
        }

        private boolean isAtEnd() {
            return index >= source.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Позиция: " + index + ".");
        }
    }
}
