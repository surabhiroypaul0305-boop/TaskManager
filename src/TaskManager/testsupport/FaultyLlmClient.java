package TaskManager.testsupport;

import TaskManager.parser.LlmClient;
import java.io.IOException;

public final class FaultyLlmClient implements LlmClient {
    public enum Fault { EMPTY_TITLE, IMPOSSIBLE_DATE, MALFORMED, THROWS }
    private final Fault fault;

    public FaultyLlmClient(Fault fault) { this.fault = fault; }

    @Override public String send(String prompt) throws IOException {
        return switch (fault) {
            case EMPTY_TITLE -> "{\"title\":\"\",\"description\":\"Plan\"}";
            case IMPOSSIBLE_DATE -> "{\"title\":\"Rent\",\"description\":\"Pay rent\",\"dueDate\":\"2026-02-30\"}";
            case MALFORMED -> "not a structured reply";
            case THROWS -> throw new IOException("Simulated provider failure");
        };
    }
}
