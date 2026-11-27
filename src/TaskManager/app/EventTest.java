package TaskManager.app;

import TaskManager.api.TaskApi;
import TaskManager.event.AuditTrailListener;
import TaskManager.model.TaskRequest;
import TaskManager.parser.RuleBasedBreakdownStrategy;
import TaskManager.parser.RuleBasedTaskParser;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.TaskService;

/** Run in a fresh JVM; events must describe a change already visible in the repository. */
public class EventTest {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        var repo = InMemoryTaskRepository.getInstance();
        var service = new TaskService(repo, new RuleBasedTaskParser(), new RuleBasedBreakdownStrategy());
        var api = new TaskApi(service);
        var audit = new AuditTrailListener();
        service.addListener(audit);
        service.addListener((type, task) -> check(repo.findById(task.getId()) ==
                (type.equals("deleted") ? null : task), "notification before persistence"));

        var open = new TaskRequest("Review", "Read and test", "false", null, null);
        var done = new TaskRequest("Review", "Read and test", "true", null, null);
        api.createTask(open, "once");
        api.createTask(open, "once");
        api.createTask(null, null);
        var parent = repo.findAll().get(0);
        service.createSubtask(parent.getId(), open);
        service.breakDownTask(parent.getId());
        service.createTaskFromText("Prepare notes tomorrow");
        check(audit.count("created") == 5, "creation paths, invalid request and replay");
        service.updateTask(parent.getId(), done);
        service.updateTask(parent.getId(), done);
        service.updateTask(parent.getId(), open);
        check(audit.count("updated") == 2 && audit.count("completed") == 1, "completion transition");
        service.deleteTask(parent.getId());
        check(audit.count("deleted") == 4 && repo.findAll().size() == 1, "cascade notifications");
        service.removeListener(audit);
        service.createTask(open);
        check(audit.count("created") == 5, "removed listener notified");
        System.out.println("Observer checks passed");
    }
}
