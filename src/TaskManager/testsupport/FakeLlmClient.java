package TaskManager.testsupport;

import TaskManager.parser.LlmClient;

/** Fixed model reply with an observable call count; no HTTP client is constructed. */
public final class FakeLlmClient implements LlmClient {
    private final String reply;
    private int calls;

    public FakeLlmClient(String reply) { this.reply = reply; }

    @Override public String send(String prompt) {
        calls++;
        return reply;
    }

    public int calls() { return calls; }
}
