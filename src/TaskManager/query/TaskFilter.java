package TaskManager.query;

import TaskManager.model.Task;

@FunctionalInterface
public interface TaskFilter {
    boolean matches(Task task);
}
