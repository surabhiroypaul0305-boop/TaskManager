package TaskManager.query;

import TaskManager.exception.ValidationException;
import TaskManager.model.Priority;
import TaskManager.model.Task;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskQuery {
    private static final Map<String, SortStrategy> BUILT_INS = Map.of(
            "createdAt", new CreatedAtSort(), "dueDate", new DueDateSort());
    private final List<TaskFilter> filters;
    private final SortStrategy sort;
    private final String order;
    private final int page, pageSize;

    private TaskQuery(Builder b) {
        List<TaskFilter> all = new ArrayList<>(b.filters);
        if (b.completed != null) all.add(new CompletedFilter(b.completed));
        if (b.priority != null) all.add(new PriorityFilter(b.priority));
        if (b.search != null && !b.search.isEmpty()) all.add(new SearchFilter(b.search));
        filters = List.copyOf(all);
        sort = b.sort;
        order = b.order;
        page = b.page;
        pageSize = b.pageSize;
    }

    public List<Task> apply(List<Task> tasks) {
        if (page < 1) throw new ValidationException("page", "must be 1 or greater");
        if (pageSize < 1) throw new ValidationException("pageSize", "must be 1 or greater");
        if (sort == null) throw new ValidationException("sortBy", "unknown sort field");
        if (!"asc".equals(order) && !"desc".equals(order)) throw new ValidationException("order", "must be asc or desc");
        Comparator<Task> comparator = sort.comparator("desc".equals(order)).thenComparingInt(Task::getId);
        var matches = tasks.stream();
        for (TaskFilter filter : filters) matches = matches.filter(filter::matches);
        return matches.sorted(comparator).skip((long) (page - 1) * pageSize).limit(pageSize).toList();
    }

    public static final class Builder {
        private Boolean completed;
        private Priority priority;
        private String search;
        private SortStrategy sort = BUILT_INS.get("createdAt");
        private String order = "asc";
        private int page = 1, pageSize = 10;
        private final List<TaskFilter> filters = new ArrayList<>();

        public Builder completed(Boolean value) { completed = value; return this; }
        public Builder priority(Priority value) { priority = value; return this; }
        public Builder search(String value) { search = value; return this; }
        public Builder sortBy(String field) { sort = BUILT_INS.get(field == null ? "createdAt" : field); return this; }
        public Builder order(String value) { order = value == null ? "asc" : value; return this; }
        public Builder page(int value) { page = value; return this; }
        public Builder pageSize(int value) { pageSize = value; return this; }
        public Builder filter(TaskFilter filter) { filters.add(Objects.requireNonNull(filter)); return this; }
        public Builder sortStrategy(SortStrategy strategy) { sort = Objects.requireNonNull(strategy); return this; }
        public TaskQuery build() { return new TaskQuery(this); }
    }
}
