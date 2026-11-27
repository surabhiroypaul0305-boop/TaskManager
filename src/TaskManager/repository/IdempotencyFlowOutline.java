package TaskManager.repository;

import TaskManager.api.ApiResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * INSTRUCTOR-PROVIDED OUTLINE (Step 6). Not a finished class - students build
 * IdempotencyStore (a Singleton) from this shape and use it in TaskApi.
 *
 * THE CHECK-THEN-STORE FLOW (runs inside createTask / createTaskFromText / breakDownTask)
 *
 *   ApiResponse createTask(TaskRequest request, String idempotencyKey) {
 *       try {
 *           String fingerprint = fingerprintOf(request);
 *
 *           // 1. CHECK  - only when a key was supplied (null key = no idempotency)
 *           if (idempotencyKey != null) {
 *               Record seen = store.find(idempotencyKey);
 *               if (seen != null) {
 *                   if (seen.fingerprint.equals(fingerprint)) {
 *                       return seen.response;                        // same key, same request -> replay
 *                   }
 *                   throw new ConflictException("Idempotency key reused with a different request"); // 422
 *               }
 *           }
 *
 *           // 2. DO THE WORK - only reached for a new key (parser is NOT called on a replay)
 *           ApiResponse response = ... validate, save, build 201 response ...
 *
 *           // 3. STORE  - only now, and only because it succeeded
 *           if (idempotencyKey != null) {
 *               store.save(idempotencyKey, fingerprint, response);
 *           }
 *           return response;
 *
 *       } catch (ApiException e) {
 *           return ErrorResponseFactory.from(e);   // errors are NEVER stored -> client can fix and retry
 *       }
 *   }
 *
 * FINGERPRINT: flatten every field of the request into one String. Careful with the separator!
 *   title + "|" + description  makes  ("a|b", "c")  and  ("a", "b|c")  IDENTICAL - two different
 *   requests that look the same. Write each value as length:value instead, e.g.  3:a|b;1:c;
 * For createTaskFromText use the text; for breakDownTask use the task id. Prefix the operation
 * name too, so the same key on two different operations also conflicts.
 *
 * SINGLETON CHECKLIST: private constructor, private static instance, public static
 * getInstance(). If threads are involved, make check-then-store atomic (synchronized).
 */
public class IdempotencyFlowOutline {

    /** What the Map stores for one key. */
    static class Record {
        final String fingerprint;
        final ApiResponse response;

        Record(String fingerprint, ApiResponse response) {
            this.fingerprint = fingerprint;
            this.response = response;
        }
    }

    // key -> record. Students: this lives inside the IdempotencyStore singleton.
    private final Map<String, Record> records = new HashMap<>();

    Record find(String key) {
        // TODO (student)
        throw new UnsupportedOperationException("not implemented yet");
    }

    void save(String key, String fingerprint, ApiResponse response) {
        // TODO (student)
        throw new UnsupportedOperationException("not implemented yet");
    }
}
