package com.townyreborn.staffconnector;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {
    }

    static String encode(Object value) {
        StringBuilder out = new StringBuilder(256);
        append(out, value);
        return out.toString();
    }

    static Object decode(String value) {
        Parser parser = new Parser(value == null ? "" : value);
        Object decoded = parser.value();
        parser.whitespace();
        if (!parser.end()) throw new IllegalArgumentException("Trailing JSON data");
        return decoded;
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            appendString(out, String.valueOf(value));
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) {
                out.append("null");
            } else if (number instanceof Float f && !Float.isFinite(f)) {
                out.append("null");
            } else {
                out.append(number);
            }
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                appendString(out, String.valueOf(entry.getKey()));
                out.append(':');
                append(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            Iterator<?> iterator = iterable.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                if (!first) out.append(',');
                first = false;
                append(out, iterator.next());
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                append(out, Array.get(value, i));
            }
            out.append(']');
        } else {
            appendString(out, String.valueOf(value));
        }
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }
        private boolean end() { return index >= source.length(); }
        private void whitespace() { while (!end() && Character.isWhitespace(source.charAt(index))) index++; }

        private Object value() {
            whitespace();
            if (end()) throw error("Missing JSON value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            whitespace();
            if (!end() && source.charAt(index) == '}') { index++; return result; }
            while (true) {
                whitespace();
                if (end() || source.charAt(index) != '"') throw error("Object key expected");
                String key = string();
                whitespace();
                if (end() || source.charAt(index++) != ':') throw error("Colon expected");
                result.put(key, value());
                whitespace();
                if (!end() && source.charAt(index) == '}') { index++; return result; }
                if (end() || source.charAt(index++) != ',') throw error("Comma expected");
            }
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            index++;
            whitespace();
            if (!end() && source.charAt(index) == ']') { index++; return result; }
            while (true) {
                result.add(value());
                whitespace();
                if (!end() && source.charAt(index) == ']') { index++; return result; }
                if (end() || source.charAt(index++) != ',') throw error("Comma expected");
            }
        }

        private String string() {
            if (source.charAt(index++) != '"') throw error("String expected");
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char character = source.charAt(index++);
                if (character == '"') return out.toString();
                if (character != '\\') { out.append(character); continue; }
                if (end()) throw error("Incomplete escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw error("Incomplete unicode escape");
                        try { out.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); }
                        catch (NumberFormatException exception) { throw error("Invalid unicode escape"); }
                        index += 4;
                    }
                    default -> throw error("Invalid escape");
                }
            }
            throw error("Unterminated string");
        }

        private Object number() {
            int start = index;
            if (!end() && source.charAt(index) == '-') index++;
            while (!end() && Character.isDigit(source.charAt(index))) index++;
            if (!end() && source.charAt(index) == '.') {
                index++;
                while (!end() && Character.isDigit(source.charAt(index))) index++;
            }
            if (!end() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (!end() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                while (!end() && Character.isDigit(source.charAt(index))) index++;
            }
            if (start == index) throw error("Number expected");
            String raw = source.substring(start, index);
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) return Double.valueOf(raw);
                return Long.valueOf(raw);
            }
            catch (NumberFormatException exception) { throw error("Invalid number"); }
        }

        private Object literal(String literal, Object result) {
            if (!source.startsWith(literal, index)) throw error("Invalid literal");
            index += literal.length();
            return result;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
