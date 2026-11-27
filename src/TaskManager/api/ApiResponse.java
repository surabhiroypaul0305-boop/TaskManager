package TaskManager.api;

import java.util.Map;

/**
 * INSTRUCTOR-PROVIDED (Step 2).
 *
 * What every TaskApi method returns: a status code plus a JSON-style body.
 *   new ApiResponse(201, task.toMap())
 *   new ApiResponse(404, errorBodyMap)
 *
 * The body can be a Map, a List, a String, a Number, a Boolean or null.
 * Convert your Task to a Map<String, Object> (e.g. a toMap() method on Task)
 * before putting it in a response - that keeps JSON out of your model classes.
 *
 * When you copy this into your own project, change the package line to: package api;
 */
public class ApiResponse {

    private final int status;
    private final Object body;

    public ApiResponse(int status, Object body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public Object getBody() {
        return body;
    }

    /** Whole response as JSON: {"status":201,"body":{...}} */
    public String toJson() {
        return "{\"status\":" + status + ",\"body\":" + valueToJson(body) + "}";
    }

    @Override
    public String toString() {
        return toJson();
    }

    // ---- tiny JSON writer (you do NOT need to understand this to use it) ----

    private static String valueToJson(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(":").append(valueToJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (v instanceof Iterable<?> items) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : items) {
                if (!first) sb.append(",");
                first = false;
                sb.append(valueToJson(item));
            }
            return sb.append("]").toString();
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
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
}
