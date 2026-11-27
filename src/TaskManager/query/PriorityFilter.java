package TaskManager.query;

import TaskManager.model.Priority;
import TaskManager.model.Task;

public record PriorityFilter(Priority priority) implements TaskFilter {
    @Override public boolean matches(Task task) { return task.getPriority() == priority; }
}
