package TaskManager.query;

import TaskManager.model.Task;
import java.util.Locale;

public record SearchFilter(String term) implements TaskFilter {
    public SearchFilter { term = term.toLowerCase(Locale.ROOT); }

    @Override public boolean matches(Task task) {
        return task.getTitle().toLowerCase(Locale.ROOT).contains(term)
                || task.getDescription().toLowerCase(Locale.ROOT).contains(term);
    }
}
