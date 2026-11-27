package TaskManager.app;
import TaskManager.event.AuditTrailListener;
import TaskManager.event.ConsoleLogListener;
import TaskManager.exception.ApiException;
import TaskManager.model.Priority;
import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import TaskManager.parser.RuleBasedBreakdownStrategy;
import TaskManager.parser.RuleBasedTaskParser;
import TaskManager.query.TaskQuery;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;
import java.util.List;
public class Main {
    public static void main(String[] args) {
        var service = new TaskService(InMemoryTaskRepository.getInstance(),
                new RuleBasedTaskParser(), new RuleBasedBreakdownStrategy());

        section("addListener / removeListener");
        var audit = new AuditTrailListener();
        service.addListener(new ConsoleLogListener());
        service.addListener(audit);
        var request = new TaskRequest("Review", "Check the changes", "false", null, null);
        Task review = service.createTask(request);
        System.out.println("Audit created count before removal: " + audit.count("created"));
        service.removeListener(audit);
        service.createTask(request);
        System.out.println("Audit created count after removal: " + audit.count("created"));

        section("createTask");
        Task report = service.createTask(new TaskRequest(
                "Write report", "Quarterly sales report", "false", "2030-01-15", "HIGH"));
        System.out.println(report);

        section("createTaskFromText");
        Task fromText = service.createTaskFromText("Call the dentist tomorrow");
        System.out.println(fromText);

        section("getTask");
        System.out.println(service.getTask(report.getId()));

        section("createSubtask");
        Task subtask = service.createSubtask(report.getId(), new TaskRequest(
                "Collect numbers", "Pull sales data", "false", null, "MEDIUM"));
        System.out.println(subtask);

        section("breakDownTask");
        print(service.breakDownTask(review.getId()));

        section("listSubtasks");
        print(service.listSubtasks(report.getId()));

        section("listTasks()");
        print(service.listTasks());

        section("listTasks(query): HIGH priority, not completed, sorted by dueDate");
        TaskQuery query = new TaskQuery.Builder()
                .priority(Priority.HIGH)
                .completed(false)
                .sortBy("dueDate")
                .order("asc")
                .build();
        print(service.listTasks(query));

        section("listByPriority(\"HIGH\")");
        print(service.listByPriority("HIGH"));

        section("updateTask (mark completed)");
        System.out.println(service.updateTask(report.getId(), new TaskRequest(
                "Write report", "Quarterly sales report", "true", "2030-01-15", "HIGH")));

        section("deleteTask (also removes its subtasks)");
        service.deleteTask(report.getId());
        System.out.println("Remaining tasks: " + service.listTasks().size());

        section("Error handling");
        tryCall("getTask(9999)", () -> service.getTask(9999));
        tryCall("createTask with empty title",
                () -> service.createTask(new TaskRequest("", "desc", "false", null, null)));
        tryCall("listByPriority(\"URGENT\")", () -> service.listByPriority("URGENT"));
    }

    private static void section(String name) {
        System.out.println("\n=== " + name + " ===");
    }

    private static void print(List<Task> tasks) {
        tasks.forEach(System.out::println);
        System.out.println("(" + tasks.size() + " tasks)");
    }

    private static void tryCall(String label, Runnable call) {
        try {
            call.run();
            System.out.println(label + ": no error");
        } catch (ApiException e) {
            System.out.println(label + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
