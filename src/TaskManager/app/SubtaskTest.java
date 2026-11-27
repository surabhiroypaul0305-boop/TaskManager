package TaskManager.app;

import TaskManager.api.ApiResponse;
import TaskManager.api.TaskApi;
import TaskManager.model.TaskRequest;
import TaskManager.parser.*;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

/** Run in a fresh JVM with LLM_API_KEY unset. No provider calls are made. */
public final class SubtaskTest {
    private static final InMemoryTaskRepository repo = InMemoryTaskRepository.getInstance();
    private static TaskApi api(BreakdownStrategy strategy) {
        return new TaskApi(new TaskService(repo, new RuleBasedTaskParser(), strategy));
    }
    private static TaskRequest request(String title) { return new TaskRequest(title, title, "false", null, null); }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static int id(ApiResponse r) {
        check(r.getStatus() == 201, r.toJson());
        return (int) ((Map<?, ?>) r.getBody()).get("id");
    }
    private static void error(ApiResponse r, int status, String code) {
        Map<?, ?> e = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        check(r.getStatus() == status && code.equals(e.get("code")) && e.get("message") instanceof String
                && e.get("details") instanceof List, r.toJson());
    }
    public static void main(String[] args) {
        TaskApi offline = api(new RuleBasedBreakdownStrategy());
        int parent = id(offline.createTask(request("Plan, implement and test"), null));
        int other = id(offline.createTask(request("Other project"), null));
        int manual = id(offline.createSubtask(parent, request("Review")));
        check(repo.findById(manual).getParentId() == parent, "manual link");
        error(offline.createSubtask(manual, request("Nested")), 400, "VALIDATION_ERROR");
        error(offline.breakDownTask(manual, null), 400, "VALIDATION_ERROR");
        error(offline.listSubtasks(-1), 404, "NOT_FOUND");
        error(offline.createSubtask(parent, request(" ")), 400, "VALIDATION_ERROR");
        var before = repo.findAll();
        for (LlmClient client : List.<LlmClient>of(prompt -> "First|Valid\nSecond| ", prompt -> "First|Valid\nwrong format",
                prompt -> { throw new HttpTimeoutException("timeout"); })) {
            error(api(new LlmBreakdownStrategy(client)).breakDownTask(parent, null), 502, "BAD_GATEWAY");
            check(before.equals(repo.findAll()), "partial save on breakdown failure");
        }
        if (System.getenv("LLM_API_KEY") == null) {
            TaskApi defaultApi = new TaskApi(new TaskService(repo));
            error(defaultApi.breakDownTask(parent, null), 503, "SERVICE_UNAVAILABLE");
            error(defaultApi.createTaskFromText("Do this", null), 503, "SERVICE_UNAVAILABLE");
            check(before.equals(repo.findAll()), "write on missing key");
        }
        var generated = api(new LlmBreakdownStrategy(prompt -> {
            check(prompt.contains("title|description") && prompt.contains("Other project"), "prompt");
            return "Draft|Write the draft\nPublish|Release it";
        })).breakDownTask(other, null);
        check(generated.getStatus() == 201 && ((List<?>) generated.getBody()).size() == 2, "LLM result");
        check(offline.breakDownTask(parent, null).getStatus() == 201, "offline breakdown");
        ApiResponse children = offline.listSubtasks(parent);
        var expected = repo.findAll().stream().filter(t -> java.util.Objects.equals(t.getParentId(), parent)).toList();
        check(children.getStatus() == 200 && expected.size() == 4 && children.getBody().equals(expected.stream().map(t -> t.toMap()).toList()), "list only this parent's children");
        System.out.println("Repository before delete: " + repo.findAll());
        check(offline.deleteTask(parent).getStatus() == 200, "delete");
        System.out.println("Repository after delete: " + repo.findAll());
        check(repo.findById(parent) == null && expected.stream().allMatch(t -> repo.findById(t.getId()) == null), "cascade");
        check(repo.findById(other) != null && repo.findAll().size() == 3, "unrelated family preserved");
        System.out.println("Subtask checks passed");
    }
}
