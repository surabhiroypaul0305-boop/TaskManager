package TaskManager.query;

import TaskManager.model.Task;
import java.util.Comparator;

public final class DueDateSort implements SortStrategy {
    @Override public Comparator<Task> comparator(boolean descending) {
        // Valid ISO dates sort lexically. Reverse only dated values, never null placement.
        Comparator<String> dates = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(Task::getDueDate, Comparator.nullsLast(dates));
    }
}
