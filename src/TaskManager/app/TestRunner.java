package TaskManager.app;

import TaskManager.service.DateHelper;
import TaskManager.parser.LlmClient;
import TaskManager.parser.ParserFactory;
import TaskManager.query.TaskQuery;
import TaskManager.testsupport.FakeLlmClient;
import TaskManager.testsupport.FaultyLlmClient;

import TaskManager.api.TaskApi;
import TaskManager.model.TaskRequest;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;
import TaskManager.api.ApiResponse;
import TaskManager.api.ErrorResponseFactory;
import TaskManager.exception.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline API acceptance checks. Run in a fresh JVM so both singletons start empty. */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- the one finished check ----
        check("2026-02-30 is rejected as a date", false, DateHelper.isValidDate("2026-02-30"));

        checkValidationResponses();
        checkModelOperations();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void checkValidationResponses() {
        var repo = InMemoryTaskRepository.getInstance();
        var api = new TaskApi(new TaskService(repo, ParserFactory.create(new FakeLlmClient("")),
                ParserFactory.createBreakdown(new FakeLlmClient(""))));
        var valid = new TaskRequest("Test", "Description", "false", "2024-02-29", "MEDIUM");
        var invalid = new TaskRequest("", " \t", "True", "2026-02-30", "low");
        var details = List.of(
                Map.of("field", "title", "issue", "must not be empty"),
                Map.of("field", "description", "issue", "must not be empty"),
                Map.of("field", "completed", "issue", "must be true or false"),
                Map.of("field", "dueDate", "issue", "must be a real date in YYYY-MM-DD format"),
                Map.of("field", "priority", "issue", "must be LOW, MEDIUM or HIGH"));
        var validationBody = errorBody("VALIDATION_ERROR", "Validation failed", details);
        ApiResponse bad = api.createTask(invalid, null);
        check("invalid create status", 400, bad.getStatus());
        check("all five field issues", validationBody, bad.getBody());
        ApiResponse created = api.createTask(valid, null);
        check("valid leap day accepted", 201, created.getStatus());
        int id = (int) ((Map<?, ?>) created.getBody()).get("id");
        ApiResponse update = api.updateTask(id, invalid);
        check("invalid update status", 400, update.getStatus());
        check("same validation on update", validationBody, update.getBody());
        check("invalid update did not change task", created.getBody(), api.getTask(id).getBody());
        check("delete existing", 200, api.deleteTask(id).getStatus());
        for (ApiResponse missing : List.of(api.getTask(id), api.updateTask(id, valid), api.deleteTask(id))) {
            check("unknown ID status", 404, missing.getStatus());
            check("unknown ID body", errorBody("NOT_FOUND", "Task " + id + " not found", List.of()), missing.getBody());
        }
        for (ApiException e : List.of(new ConflictException("conflict"), new BadGatewayException("gateway"),
                new ServiceUnavailableException("unavailable"))) {
            ApiResponse response = ErrorResponseFactory.from(e);
            check("status " + e.getCode(), switch (e.getCode()) {
                case "CONFLICT" -> 422; case "BAD_GATEWAY" -> 502; case "SERVICE_UNAVAILABLE" -> 503;
                default -> -1;
            }, response.getStatus());
            check("body " + e.getCode(), errorBody(e.getCode(), e.getMessage(), List.of()), response.getBody());
        }
        var failingApi = new TaskApi(new TaskService(repo, ParserFactory.create(new FakeLlmClient("")),
                ParserFactory.createBreakdown(new FakeLlmClient(""))) {
            @Override public List<TaskManager.model.Task> listTasks() { throw new IllegalStateException("private diagnostic"); }
        });
        ApiResponse failure = failingApi.listTasks();
        check("unexpected failure status", 500, failure.getStatus());
        check("unexpected failure is sanitized", errorBody("INTERNAL_ERROR", "Internal server error", List.of()), failure.getBody());
    }

    private static TaskApi offlineApi(LlmClient parser, LlmClient breakdown) {
        return new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                ParserFactory.create(parser), ParserFactory.createBreakdown(breakdown)));
    }

    private static void checkModelOperations() {
        var repo = InMemoryTaskRepository.getInstance();
        var parser = new FakeLlmClient("{\"title\":\"Plan\",\"description\":\"Prepare release\",\"dueDate\":\"2028-02-29\"}");
        var breakdown = new FakeLlmClient("Draft|Write notes\nReview|Check notes");
        var api = offlineApi(parser, breakdown);
        check("blank text rejected before parsing", 400, api.createTaskFromText("  ", null).getStatus());
        check("blank text makes no model call", 0, parser.calls());
        var before = repo.findAll();
        for (var fault : FaultyLlmClient.Fault.values()) {
            var faulty = offlineApi(new FaultyLlmClient(fault), breakdown);
            var result = faulty.createTaskFromText("Prepare release", "recover");
            check(fault + " status", 502, result.getStatus());
            check(fault + " error code", "BAD_GATEWAY", ((Map<?, ?>) ((Map<?, ?>) result.getBody()).get("error")).get("code"));
            check(fault + " leaves repository unchanged", before, repo.findAll());
        }
        var created = api.createTaskFromText("Prepare release", "recover");
        check("failed key can be retried", 201, created.getStatus());
        check("text replay returns original response", created.toJson(), api.createTaskFromText("Prepare release", "recover").toJson());
        check("changed text conflicts", 422, api.createTaskFromText("Different", "recover").getStatus());
        check("text replay and conflict skip fake", 1, parser.calls());
        check("only one parent saved", before.size() + 1, repo.findAll().size());
        int parent = (int) ((Map<?, ?>) created.getBody()).get("id");
        var parentsOnly = repo.findAll();
        var badBreakdown = offlineApi(parser, new FakeLlmClient("Draft|Valid first item\nReview|"));
        check("invalid second subtask", 502, badBreakdown.breakDownTask(parent, "split").getStatus());
        check("no partial breakdown saved", parentsOnly, repo.findAll());
        var split = api.breakDownTask(parent, "split");
        check("valid breakdown after failure", 201, split.getStatus());
        check("breakdown replay returns original", split.toJson(), api.breakDownTask(parent, "split").toJson());
        check("breakdown fake called once", 1, breakdown.calls());
        var children = (List<?>) api.listSubtasks(parent).getBody();
        check("exactly two children saved", 2, children.size());
        int child = (int) ((Map<?, ?>) children.get(0)).get("id");
        var request = new TaskRequest("Nested", "Invalid parent", "false", null, null);
        check("cannot add child to subtask", 400, api.createSubtask(child, request).getStatus());
        check("cannot break down a subtask", 400, api.breakDownTask(child, null).getStatus());
        check("nested breakdown skipped fake", 1, breakdown.calls());
        var page = api.listTasks(new TaskQuery.Builder().page(99).build());
        check("past last page status", 200, page.getStatus());
        check("past last page body", List.of(), page.getBody());
        api.deleteTask(parent);
    }

    private static Object errorBody(String code, String message, List<?> details) {
        return Map.of("error", Map.of("code", code, "message", message, "details", details));
    }

    /** call -> compare -> print. 'expected' and 'actual' can be any type; equals() decides. */
    static void check(String name, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  (expected: " + expected + ", actual: " + actual + ")");
        }
    }
}
