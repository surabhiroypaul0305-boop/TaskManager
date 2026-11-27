package TaskManager.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * INSTRUCTOR-PROVIDED (Steps 4, 5). Reading JSON was not covered in class.
 *
 * parseFlat(text)  - reads ONE flat JSON object into a Map<String, String>.
 *     {"title":"Pay rent","dueDate":null,"count":3}
 *       -> title=Pay rent, dueDate=null (a Java null), count=3 (as text)
 *     * Everything comes back as a String - validate/convert it yourself ("never trust the model").
 *     * Tolerates ```json code fences, which models often add even when told not to.
 *     * Nested objects/arrays are returned as their raw text.
 *     * Throws IllegalArgumentException if the text is not a JSON object -> catch it and
 *       turn it into a 502 (the model produced garbage).
 *
 * firstStringValue(json, key) - finds the first "key":"..." anywhere (even nested).
 *     Used by HttpLlmClient to pull the reply text out of a provider's response.
 *
 * quote(s) - wraps s in quotes and escapes it, for building request JSON.
 *
 * When you copy this into your own project, change the package line to: package parser;
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    public static Map<String, String> parseFlat(String text) {
        return parseObject(text, false);
    }

    /** Like parseFlat, but never coerces numbers, booleans or nested values to strings. */
    public static Map<String, String> parseStringFields(String text) {
        return parseObject(text, true);
    }

    private static Map<String, String> parseObject(String text, boolean stringsOnly) {
        if (text == null) {
            throw new IllegalArgumentException("Reply is null");
        }
        Reader r = new Reader(stripFences(text));
        r.skipWhitespace();
        r.expect('{');
        Map<String, String> result = new LinkedHashMap<>();
        r.skipWhitespace();
        if (r.peek() != '}') {
            while (true) {
                r.skipWhitespace();
                String key = r.readString();
                r.skipWhitespace();
                r.expect(':');
                r.skipWhitespace();
                if (stringsOnly && (result.containsKey(key) ||
                        (r.peek() != '"' && !r.s.startsWith("null", r.pos)))) {
                    throw new IllegalArgumentException("Expected unique string or null field");
                }
                boolean quoted = r.peek() == '"';
                String value = r.readValue();
                if (stringsOnly && !quoted && value != null) throw new IllegalArgumentException("Invalid null");
                result.put(key, value);
                r.skipWhitespace();
                if (r.peek() == '}') break;
                r.expect(',');
            }
        }
        r.expect('}');
        r.skipWhitespace();
        if (r.pos != r.s.length()) throw new IllegalArgumentException("Unexpected trailing text");
        return result;
    }

    public static String firstStringValue(String json, String key) {
        Reader r = new Reader(json);
        while (r.pos < r.s.length()) {
            if (r.s.charAt(r.pos) != '"') {
                r.pos++;
                continue;
            }
            String candidate = r.readString();
            r.skipWhitespace();
            if (r.pos < r.s.length() && r.s.charAt(r.pos) == ':' && candidate.equals(key)) {
                r.pos++;
                r.skipWhitespace();
                if (r.pos < r.s.length() && r.s.charAt(r.pos) == '"') {
                    return r.readString();
                }
            }
        }
        return null;
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            t = firstNewline < 0 ? "" : t.substring(firstNewline + 1);
            int closing = t.lastIndexOf("```");
            if (closing >= 0) {
                t = t.substring(0, closing);
            }
        }
        return t.trim();
    }

    /** Walks through the text one character at a time. */
    private static final class Reader {
        final String s;
        int pos = 0;

        Reader(String s) {
            this.s = s;
        }

        char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return s.charAt(pos);
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        /** Positioned on an opening quote; returns the unescaped text and moves past the closing quote. */
        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    if (c < 0x20) throw new IllegalArgumentException("Unescaped control character");
                    sb.append(c);
                    continue;
                }
                char e = peek();
                pos++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw new IllegalArgumentException("Bad \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("Bad \\u escape");
                        }
                        pos += 4;
                    }
                    case '"', '\\', '/' -> sb.append(e);
                    default -> throw new IllegalArgumentException("Invalid string escape");
                }
            }
        }

        /** A string, a nested {...}/[...] (raw text), or a bare token such as 3, true, null. */
        String readValue() {
            char c = peek();
            if (c == '"') {
                return readString();
            }
            if (c == '{' || c == '[') {
                int start = pos;
                skipBalanced();
                return s.substring(start, pos);
            }
            int start = pos;
            while (pos < s.length() && ",}".indexOf(s.charAt(pos)) < 0 && !Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
            String raw = s.substring(start, pos);
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("Missing value at position " + start);
            }
            return raw.equals("null") ? null : raw;
        }

        void skipBalanced() {
            int depth = 0;
            while (true) {
                char c = peek();
                if (c == '"') {
                    readString();
                    continue;
                }
                pos++;
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) {
                        return;
                    }
                }
            }
        }
    }
}
