package TaskManager.app;

import TaskManager.parser.HttpLlmClient;
import TaskManager.parser.LlmClient;
import TaskManager.parser.SimpleJson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * INSTRUCTOR TOOL - proves HttpLlmClient builds the right request for each provider WITHOUT a key
 * or internet: it starts a tiny fake server on localhost that answers like Anthropic, OpenAI and
 * Gemini do, then checks the path, the headers, the body, and the reply parsing.
 * Run it after editing HttpLlmClient (or after a provider changes its API).
 */
public class LlmClientSelfCheck {

    private static String lastPath;
    private static String lastBody;
    private static final Map<String, String> lastHeaders = new HashMap<>();
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k.toLowerCase(), v.get(0)));
            int status = 200;
            String reply;
            String key = lastHeaders.getOrDefault("x-api-key", "");
            if (key.equals("bad")) {
                status = 401;
                reply = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"Incorrect API key provided: sk-abc****xyz\"}}";
            } else if (key.equals("missing-model")) {
                status = 404;
                reply = "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"model: nope\"}}";
            } else if (lastPath.equals("/v1/messages")) {
                reply = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\","
                        + "\"text\":\"{\\\"title\\\":\\\"Pay rent\\\"}\"}],\"stop_reason\":\"end_turn\","
                        + "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}";
            } else if (lastPath.equals("/v1/chat/completions")) {
                reply = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"title\\\":\\\"Pay rent\\\"}\","
                        + "\"refusal\":null,\"annotations\":[]},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1}}";
            } else if (lastPath.endsWith(":generateContent")) {
                reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"title\\\":\\\"Pay rent\\\"}\"}],"
                        + "\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":1}}";
            } else {
                status = 404;
                reply = "{\"error\":{\"message\":\"no such endpoint\"}}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        // A prompt full of characters that must be escaped correctly inside the request JSON
        String prompt = "Line one\n\"quoted\" back\\slash\ttab and unicode é中";

        LlmClient anthropic = new HttpLlmClient("k1", "anthropic", null, base);
        check("anthropic returns the reply text", "{\"title\":\"Pay rent\"}", anthropic.send(prompt));
        check("anthropic path", "/v1/messages", lastPath);
        check("anthropic x-api-key header", "k1", lastHeaders.get("x-api-key"));
        check("anthropic version header", "2023-06-01", lastHeaders.get("anthropic-version"));
        check("anthropic sends max_tokens (required by the API)", true, lastBody.contains("\"max_tokens\":"));
        check("anthropic prompt survives escaping", prompt, SimpleJson.firstStringValue(lastBody, "content"));

        LlmClient openai = new HttpLlmClient("k2", "OpenAI", "some-model", base);
        check("openai returns the reply text", "{\"title\":\"Pay rent\"}", openai.send(prompt));
        check("openai path", "/v1/chat/completions", lastPath);
        check("openai Bearer header", "Bearer k2", lastHeaders.get("authorization"));
        check("openai model from LLM_MODEL", "some-model", SimpleJson.firstStringValue(lastBody, "model"));
        check("openai prompt survives escaping", prompt, SimpleJson.firstStringValue(lastBody, "content"));

        LlmClient gemini = new HttpLlmClient("k3", "gemini", "gem-1", base);
        check("gemini returns the reply text", "{\"title\":\"Pay rent\"}", gemini.send(prompt));
        check("gemini path has the model", "/v1beta/models/gem-1:generateContent", lastPath);
        check("gemini x-goog-api-key header", "k3", lastHeaders.get("x-goog-api-key"));
        check("gemini prompt survives escaping", prompt, SimpleJson.firstStringValue(lastBody, "text"));

        check("401 -> IOException", true, fails(new HttpLlmClient("bad", "anthropic", null, base)));
        check("401 message does not echo the provider's text (it may contain part of a key)", false,
                messageOf(new HttpLlmClient("bad", "anthropic", null, base)).contains("sk-abc"));
        check("404 message includes the provider's explanation", true,
                messageOf(new HttpLlmClient("missing-model", "anthropic", null, base)).contains("model: nope"));
        check("unknown provider -> IOException", true, fails(new HttpLlmClient("k", "nope", null, base)));
        check("invalid model name -> IOException (not a crash)", true,
                fails(new HttpLlmClient("k", "gemini", "bad model name", base)));
        check("connection refused -> IOException", true,
                fails(new HttpLlmClient("k", "anthropic", null, "http://127.0.0.1:1")));

        server.stop(0);
        System.out.println();
        System.out.println(failures == 0 ? "All HttpLlmClient checks passed." : failures + " check(s) FAILED");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static boolean fails(LlmClient client) {
        return !messageOf(client).isEmpty();
    }

    /** Message of the IOException the client throws, or "" if it did not throw. */
    private static String messageOf(LlmClient client) {
        try {
            client.send("x");
            return "";
        } catch (IOException e) {
            return e.getMessage() == null ? "(no message)" : e.getMessage();
        }
    }

    private static void check(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (ok ? "" : "  (expected: " + expected + ", actual: " + actual + ")"));
        if (!ok) {
            failures++;
        }
    }
}
