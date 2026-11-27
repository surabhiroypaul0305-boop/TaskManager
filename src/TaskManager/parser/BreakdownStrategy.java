package TaskManager.parser;

import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import java.util.List;

/** Proposes subtasks without writing to the repository. */
@FunctionalInterface
public interface BreakdownStrategy {
    List<TaskRequest> breakDown(Task parent);
}
