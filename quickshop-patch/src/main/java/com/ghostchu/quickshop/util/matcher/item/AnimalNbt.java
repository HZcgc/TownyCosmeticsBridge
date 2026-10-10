package com.ghostchu.quickshop.util.matcher.item;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Read-only, bounded NBT/SNBT reader for comparison. No server internals, item
 * rewriting or third-party plugin dependency. Numeric and array types remain distinct.
 */
final class AnimalNbt {
    static final int MAX_BYTES = 1_048_576;
    private static final int MAX_TAGS = 65_536, MAX_DEPTH = 64;

    record Tag(int type, Object value) {}
    record TagList(int elementType, List<Tag> values) {}

    @SuppressWarnings("unchecked")
    static Map<String, Tag> compound(Tag tag) {
        if (tag == null || tag.type() != 10) throw new IllegalArgumentException("Expected compound");
        return (Map<String, Tag>) tag.value();
    }

    static Tag string(String value) { return new Tag(8, value); }

    static Tag read(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Oversized NBT");
        InputStream input = new ByteArrayInputStream(bytes);
        if (bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139)
            input = new GZIPInputStream(input);
        byte[] raw;
        try (InputStream stream = input) { raw = stream.readNBytes(MAX_BYTES + 1); }
        if (raw.length > MAX_BYTES) throw new IOException("Oversized expanded NBT");
        try (var stream = new DataInputStream(new ByteArrayInputStream(raw))) {
            int type = stream.readUnsignedByte();
            if (type != 10) throw new IOException("Expected NBT root compound");
            stream.readUTF();
            Tag result = new BinaryReader(stream).value(type, 0);
            if (stream.read() != -1) throw new IOException("Trailing NBT");
            return result;
        }
    }

    private static final class BinaryReader {
        final DataInputStream input;
        int tags;
        BinaryReader(DataInputStream input) { this.input = input; }

        int length() throws IOException {
            int length = input.readInt();
            if (length < 0 || length > MAX_TAGS) throw new IOException("Invalid NBT length");
            return length;
        }

        Tag value(int type, int depth) throws IOException {
            if (++tags > MAX_TAGS || depth > MAX_DEPTH) throw new IOException("NBT limit");
            Object result = switch (type) {
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 8 -> input.readUTF();
                case 7, 11, 12 -> {
                    int length = length();
                    var array = new ArrayList<Number>(length);
                    for (int i = 0; i < length; i++) array.add(switch (type) {
                        case 7 -> input.readByte();
                        case 11 -> input.readInt();
                        default -> input.readLong();
                    });
                    yield array;
                }
                case 9 -> {
                    int child = input.readUnsignedByte(), length = length();
                    if (child > 12 || (child == 0 && length != 0)) throw new IOException("Invalid NBT list");
                    var list = new ArrayList<Tag>(length);
                    for (int i = 0; i < length; i++) list.add(value(child, depth + 1));
                    yield new TagList(child, list);
                }
                case 10 -> {
                    var map = new HashMap<String, Tag>();
                    for (int child; (child = input.readUnsignedByte()) != 0;) {
                        String key = input.readUTF();
                        if (map.putIfAbsent(key, value(child, depth + 1)) != null)
                            throw new IOException("Duplicate NBT key");
                    }
                    yield map;
                }
                default -> throw new IOException("Invalid NBT type");
            };
            return new Tag(type, result);
        }
    }

    static Tag parse(String snbt) {
        if (snbt == null || snbt.length() > MAX_BYTES) throw new IllegalArgumentException("Oversized SNBT");
        var parser = new SnbtReader(snbt);
        Tag result = parser.value(0);
        parser.space();
        if (parser.position != snbt.length() || result.type() != 10)
            throw new IllegalArgumentException("Invalid SNBT root");
        return result;
    }

    private static final class SnbtReader {
        final String text;
        int position, tags;
        SnbtReader(String text) { this.text = text; }
        void space() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }
        char peek() {
            space();
            if (position >= text.length()) throw new IllegalArgumentException("Unexpected SNBT end");
            return text.charAt(position);
        }
        boolean take(char expected) {
            space();
            if (position < text.length() && text.charAt(position) == expected) { position++; return true; }
            return false;
        }
        void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Invalid SNBT punctuation");
        }
        String quoted() {
            char quote = text.charAt(position++);
            var value = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == quote) return value.toString();
                if (c == '\\') {
                    if (position == text.length()) throw new IllegalArgumentException("Invalid SNBT escape");
                    c = text.charAt(position++);
                    if (c != '\\' && c != quote) throw new IllegalArgumentException("Invalid SNBT escape");
                }
                value.append(c);
            }
            throw new IllegalArgumentException("Unterminated SNBT string");
        }
        String token(boolean key) {
            space();
            if (peek() == '"' || peek() == '\'') return quoted();
            int start = position;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (Character.isWhitespace(c) || c == ',' || c == '}' || c == ']' || (key && c == ':')) break;
                if ("{}[;\"'".indexOf(c) >= 0) throw new IllegalArgumentException("Invalid SNBT token");
                position++;
            }
            if (position == start) throw new IllegalArgumentException("Missing SNBT token");
            return text.substring(start, position);
        }
        Tag value(int depth) {
            if (++tags > MAX_TAGS || depth > MAX_DEPTH) throw new IllegalArgumentException("SNBT limit");
            char c = peek();
            if (c == '{') {
                position++;
                var map = new HashMap<String, Tag>();
                if (!take('}')) {
                    do {
                        if (take('}')) return new Tag(10, map);
                        String key = token(true);
                        expect(':');
                        if (map.putIfAbsent(key, value(depth + 1)) != null)
                            throw new IllegalArgumentException("Duplicate SNBT key");
                    } while (take(','));
                    expect('}');
                }
                return new Tag(10, map);
            }
            if (c == '[') {
                position++;
                space();
                int arrayType = 0, childType = 0;
                if (position + 1 < text.length() && text.charAt(position + 1) == ';') {
                    char type = text.charAt(position);
                    arrayType = switch (type) { case 'B' -> 7; case 'I' -> 11; case 'L' -> 12; default -> 0; };
                    if (arrayType == 0) throw new IllegalArgumentException("Invalid SNBT array");
                    childType = switch (arrayType) { case 7 -> 1; case 11 -> 3; default -> 4; };
                    position += 2;
                }
                var list = new ArrayList<Tag>();
                if (!take(']')) {
                    do {
                        if (take(']')) break;
                        Tag element = value(depth + 1);
                        if (childType == 0) childType = element.type();
                        if (childType != element.type()) throw new IllegalArgumentException("Mixed SNBT list");
                        list.add(element);
                        if (list.size() > MAX_TAGS) throw new IllegalArgumentException("SNBT list limit");
                        if (!take(',')) { expect(']'); break; }
                    } while (true);
                }
                if (arrayType != 0) return new Tag(arrayType, list.stream().map(Tag::value).toList());
                return new Tag(9, new TagList(childType, list));
            }
            if (c == '"' || c == '\'') return string(quoted());
            String token = token(false);
            if (token.equalsIgnoreCase("true")) return new Tag(1, (byte) 1);
            if (token.equalsIgnoreCase("false")) return new Tag(1, (byte) 0);
            try {
                char suffix = Character.toLowerCase(token.charAt(token.length() - 1));
                String number = token.substring(0, token.length() - 1);
                if (token.matches("[-+]?\\d+[bBsSlL]")) return switch (suffix) {
                    case 'b' -> new Tag(1, Byte.parseByte(number));
                    case 's' -> new Tag(2, Short.parseShort(number));
                    default -> new Tag(4, Long.parseLong(number));
                };
                if (token.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?[fFdD]"))
                    return suffix == 'f' ? new Tag(5, Float.parseFloat(number)) : new Tag(6, Double.parseDouble(number));
                if (token.matches("[-+]?\\d+")) return new Tag(3, Integer.parseInt(token));
                if (token.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?"))
                    return new Tag(6, Double.parseDouble(token));
            } catch (NumberFormatException ignored) { /* Mojang also retains non-numeric strings. */ }
            return string(token);
        }
    }
}
