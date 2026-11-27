package TaskManager.app;

import TaskManager.api.ApiResponse;
import TaskManager.api.TaskApi;
import TaskManager.model.Priority;
import TaskManager.model.Task;
import TaskManager.parser.RuleBasedBreakdownStrategy;
import TaskManager.parser.RuleBasedTaskParser;
import TaskManager.query.TaskQuery;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Run in a fresh JVM. Uses the real repository and service, with no provider calls. */
public final class QueryTest {
    private static final InMemoryTaskRepository repo = InMemoryTaskRepository.getInstance();
    private static final TaskApi api = new TaskApi(new TaskService(repo,
            new RuleBasedTaskParser(), new RuleBasedBreakdownStrategy()));

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    private static int seed(String title, String description, boolean done, Priority priority,
                            String due, long createdAt) {
        return repo.save(new Task.TaskBuilder(title, description, done).priority(priority)
                .dueDate(due).createdAt(createdAt).build()).getId();
    }

    private static List<Integer> ids(ApiResponse response) {
        check(response.getStatus() == 200 && response.getBody() instanceof List<?>, response.toJson());
        return ((List<?>) response.getBody()).stream()
                .map(row -> (Integer) ((Map<?, ?>) row).get("id")).toList();
    }

    private static void expect(String label, ApiResponse response, List<Integer> expected) {
        var actual = ids(response);
        check(actual.equals(expected), label + ": expected " + expected + ", got " + actual);
    }

    private static void priority(String level, List<Integer> expected) {
        // listByPriority promises all matches, not a particular order.
        var actual = ids(api.listByPriority(level)).stream().sorted().toList();
        check(actual.equals(expected.stream().sorted().toList()), level + ": got " + actual);
    }

    private static void invalid(ApiResponse response, String field) {
        check(response.getStatus() == 400 && response.getBody() instanceof Map<?, ?> body
                && body.get("error") instanceof Map<?, ?> error
                && "VALIDATION_ERROR".equals(error.get("code")) && error.get("message") instanceof String
                && error.get("details") instanceof List<?> details && details.stream().anyMatch(item ->
                    item instanceof Map<?, ?> d && field.equals(d.get("field"))
                            && d.get("issue") instanceof String issue && !issue.isBlank()),
                "expected validation error for " + field + ": " + response.toJson());
    }

    public static void main(String[] args) {
        check(repo.findAll().isEmpty(), "Run QueryTest in a fresh JVM");
        priority("HIGH", List.of());
        int late = seed("Audit ledger", "quarterly", false, Priority.HIGH, "2027-04-01", 40);
        int early = seed("Balance", "preAUDIT notes", false, Priority.HIGH, "2027-02-01", 20);
        int undated = seed("Desk audit", "review", false, Priority.HIGH, null, 10);
        // Each distractor fails exactly one clause and would sort before the matches.
        int done = seed("Audit closed", "review", true, Priority.HIGH, "2027-01-01", 30);
        int low = seed("Audit later", "review", false, Priority.LOW, "2027-01-02", 50);
        int unrelated = seed("Budget", "review", false, Priority.HIGH, "2027-01-03", 60);

        var combined = new TaskQuery.Builder().completed(false).priority(Priority.HIGH)
                .search("aUdIt").sortBy("dueDate").order("asc");
        TaskQuery ascending = combined.build();
        TaskQuery descending = combined.order("desc").build();
        TaskQuery secondPage = combined.pageSize(1).page(2).build();
        expect("ascending snapshot, null last", api.listTasks(ascending), List.of(early, late, undated));
        expect("descending snapshot, null last", api.listTasks(descending), List.of(late, early, undated));
        expect("combined second page", api.listTasks(secondPage), List.of(early));
        expect("past last matching page", api.listTasks(combined.page(4).build()), List.of());
        expect("completed only", api.listTasks(new TaskQuery.Builder().completed(true).build()), List.of(done));

        int medium = seed("Admin", "review", false, Priority.MEDIUM, null, 70);
        List<Integer> high = new ArrayList<>(List.of(late, early, undated, done, unrelated));
        List<Integer> chronological = new ArrayList<>(List.of(undated, early, done, late, low, unrelated, medium));
        for (int i = 0; i < 7; i++) {
            int id = seed("Extra " + i, "filing", false, Priority.HIGH, null, 80 + i);
            high.add(id);
            chronological.add(id);
        }
        var firstTen = chronological.subList(0, 10);
        expect("no-arg defaults", api.listTasks(), firstTen);
        expect("null defaults", api.listTasks(null), firstTen);
        expect("builder defaults", api.listTasks(new TaskQuery.Builder().build()), firstTen);
        expect("partial final page", api.listTasks(new TaskQuery.Builder().page(2).build()),
                chronological.subList(10, chronological.size()));
        var newestFirst = new ArrayList<>(chronological);
        java.util.Collections.reverse(newestFirst);
        expect("createdAt desc", api.listTasks(new TaskQuery.Builder().sortBy("createdAt")
                .order("desc").pageSize(chronological.size()).build()), newestFirst);

        for (int bad : List.of(0, -1)) {
            invalid(api.listTasks(new TaskQuery.Builder().page(bad).build()), "page");
            invalid(api.listTasks(new TaskQuery.Builder().pageSize(bad).build()), "pageSize");
        }
        expect("large valid page", api.listTasks(new TaskQuery.Builder().page(Integer.MAX_VALUE).build()), List.of());
        invalid(api.listTasks(new TaskQuery.Builder().sortBy("unknown").build()), "sortBy");
        invalid(api.listTasks(new TaskQuery.Builder().order("sideways").build()), "order");
        priority("HIGH", high);
        priority("MEDIUM", List.of(medium));
        priority("LOW", List.of(low));
        for (String bad : new String[]{null, "", "high", "URGENT"}) invalid(api.listByPriority(bad), "level");

        var custom = new TaskQuery.Builder().priority(Priority.HIGH)
                .filter(t -> "review".equals(t.getDescription()))
                .sortStrategy(desc -> {
                    Comparator<Task> byTitle = Comparator.comparing(Task::getTitle);
                    return desc ? byTitle.reversed() : byTitle;
                });
        expect("custom filter and title asc", api.listTasks(custom.order("asc").build()),
                List.of(done, unrelated, undated));
        expect("custom filter and title desc", api.listTasks(custom.order("desc").build()),
                List.of(undated, unrelated, done));
        System.out.println("Query checks passed");
    }
}
