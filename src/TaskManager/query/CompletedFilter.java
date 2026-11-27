package TaskManager.query;

import TaskManager.model.Task;

public record CompletedFilter(boolean completed) implements TaskFilter {
    @Override public boolean matches(Task task) { return task.isCompleted() == completed; }
}
