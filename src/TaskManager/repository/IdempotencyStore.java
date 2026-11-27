package TaskManager.repository;

import TaskManager.api.ApiResponse;
import TaskManager.exception.ConflictException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class IdempotencyStore {
    private record Entry(String fingerprint, ApiResponse response) {}

    private static final IdempotencyStore INSTANCE = new IdempotencyStore();
    private final Map<String, Entry> records = new HashMap<>();

    private IdempotencyStore() {}

    public static IdempotencyStore getInstance() { return INSTANCE; }

    public ApiResponse execute(String key, String fingerprint, Supplier<ApiResponse> operation) {
        if (key == null) return operation.get();
        synchronized (this) {
            Entry prior = records.get(key);
            if (prior != null) {
                if (prior.fingerprint().equals(fingerprint)) return prior.response();
                throw new ConflictException("Idempotency key reused with a different request");
            }
            ApiResponse result = operation.get();
            if (result.getStatus() >= 200 && result.getStatus() < 300) {
                records.put(key, new Entry(fingerprint, result));
            }
            return result;
        }
    }
}
