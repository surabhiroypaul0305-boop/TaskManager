package TaskManager.parser;

import TaskManager.exception.ServiceUnavailableException;
import TaskManager.parser.HttpLlmClient;

public final class ParserFactory {
    private ParserFactory() {}

    public static TaskParser create(LlmClient client) {
        return new LlmTaskParser(client);
    }

    public static BreakdownStrategy createBreakdown(LlmClient client) {
        return new LlmBreakdownStrategy(client);
    }

    public static BreakdownStrategy breakdownFromEnvironment() {
        String key = System.getenv("LLM_API_KEY");
        if (key == null || key.isBlank()) {
            return parent -> { throw new ServiceUnavailableException("LLM_API_KEY is not configured"); };
        }
        return createBreakdown(new HttpLlmClient(key, System.getenv("LLM_PROVIDER"), System.getenv("LLM_MODEL")));
    }

    public static TaskParser fromEnvironment(boolean allowOfflineFallback) {
        return create(System.getenv("LLM_API_KEY"), System.getenv("LLM_PROVIDER"),
                System.getenv("LLM_MODEL"), allowOfflineFallback);
    }

    public static TaskParser create(String key, String provider, String model, boolean allowOfflineFallback) {
        if (key != null && !key.isBlank()) return create(new HttpLlmClient(key, provider, model));
        if (allowOfflineFallback) return new RuleBasedTaskParser();
        // Fail only the AI operation; ordinary CRUD needs no provider credentials.
        return (text, today) -> { throw new ServiceUnavailableException("LLM_API_KEY is not configured"); };
    }
}
