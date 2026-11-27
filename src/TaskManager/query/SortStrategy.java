package TaskManager.query;

import TaskManager.model.Task;
import java.util.Comparator;

@FunctionalInterface
public interface SortStrategy {
    Comparator<Task> comparator(boolean descending);
}
