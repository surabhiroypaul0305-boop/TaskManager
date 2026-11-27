package TaskManager.app;

import TaskManager.api.ApiResponse;
import TaskManager.api.TaskApi;
import TaskManager.model.TaskRequest;
import TaskManager.parser.ParsedTask;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Run in a fresh JVM; both strategies are offline counting fakes. */
public final class IdempotencyTest {
    private static TaskRequest request(String title, String description) {
        return new TaskRequest(title, description, "false", null, null);
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void conflict(ApiResponse response) {
        check(response.getStatus() == 422 && response.getBody().equals(Map.of("error", Map.of(
                "code", "CONFLICT", "message", "Idempotency key reused with a different request", "details", List.of()))), response.toJson());
    }
    public static void main(String[] args) throws Exception {
        var repo = InMemoryTaskRepository.getInstance();
        var parses = new AtomicInteger();
        var breakdowns = new AtomicInteger();
        var service = new TaskService(repo, (text, today) -> {
            parses.incrementAndGet();
            return new ParsedTask(text, "description", null, null);
        }, parent -> {
            breakdowns.incrementAndGet();
            return List.of(request("child", "details"));
        });
        var api = new TaskApi(service);
        var retryApi = new TaskApi(service);
        var original = api.createTask(request("a|b", "c"), "create");
        var replay = retryApi.createTask(request("a|b", "c"), "create");
        check(original.getStatus() == 201 && replay == original && repo.findAll().size() == 1, "duplicate creation");
        System.out.println("Repository after two createTask calls with key 'create': " + repo.findAll());
        conflict(api.createTask(request("a", "b|c"), "create"));
        conflict(api.createTaskFromText("a|b", "create"));
        check(parses.get() == 0, "parser called on conflict");
        check(api.createTask(request("", "c"), "fix").getStatus() == 400, "expected failure");
        check(api.createTask(request("fixed", "c"), "fix").getStatus() == 201, "failure reserved key");
        var broken = api.breakDownTask(1, "break");
        check(broken.getStatus() == 201 && api.breakDownTask(1, "break") == broken && breakdowns.get() == 1, "breakdown replay");
        conflict(api.breakDownTask(2, "break"));
        int before = repo.findAll().size();
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ApiResponse> call = () -> { start.await(); return api.createTaskFromText("concurrent", "text"); };
            var a = pool.submit(call);
            var b = pool.submit(call);
            start.countDown();
            var first = a.get(5, TimeUnit.SECONDS);
            check(first.getStatus() == 201 && first == b.get(5, TimeUnit.SECONDS), "concurrent replay");
        } finally { pool.shutdownNow(); }
        conflict(api.createTaskFromText("different", "text"));
        check(parses.get() == 1 && repo.findAll().size() == before + 1, "extra parser call or write");
        api.createTaskFromText("same", null);
        api.createTaskFromText("same", null);
        check(parses.get() == 3 && repo.findAll().size() == before + 3, "null key must bypass cache");
        System.out.println("Idempotency checks passed");
    }
}
