package TaskManager.query;

import TaskManager.model.Task;
import java.util.Comparator;

public final class CreatedAtSort implements SortStrategy {
    @Override public Comparator<Task> comparator(boolean descending) {
        Comparator<Task> byTime = Comparator.comparingLong(Task::getCreatedAt);
        return descending ? byTime.reversed() : byTime;
    }
}
