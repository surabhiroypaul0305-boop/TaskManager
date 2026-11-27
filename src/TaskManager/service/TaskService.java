package TaskManager.service;

import TaskManager.event.TaskEventListener;
import java.util.concurrent.CopyOnWriteArrayList;
import TaskManager.model.Priority;
import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import TaskManager.parser.BreakdownStrategy;
import TaskManager.parser.ParsedTask;
import TaskManager.parser.ParserFactory;
import TaskManager.parser.TaskParser;
import TaskManager.exception.BadGatewayException;
import TaskManager.repository.TaskRepository;
import TaskManager.query.TaskQuery;
import TaskManager.query.PriorityFilter;
import TaskManager.exception.FieldIssue;
import TaskManager.exception.NotFoundException;
import TaskManager.exception.ValidationException;
import TaskManager.service.DateHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TaskService {
    private final TaskRepository repository;

    private final TaskParser parser;

    public TaskService(TaskRepository repository) {
        this(repository, ParserFactory.fromEnvironment(false));
    }

    private final BreakdownStrategy breakdown;

    public TaskService(TaskRepository repository, TaskParser parser) {
        this(repository, parser, ParserFactory.breakdownFromEnvironment());
    }

    public TaskService(TaskRepository repository, TaskParser parser, BreakdownStrategy breakdown) {
        this.repository = Objects.requireNonNull(repository);
        this.parser = Objects.requireNonNull(parser);
        this.breakdown = Objects.requireNonNull(breakdown);
    }

    private final CopyOnWriteArrayList<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(TaskEventListener listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener));
    }

    public void removeListener(TaskEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String eventType, Task task) {
        for (TaskEventListener listener : listeners) {
            try {
                listener.onEvent(eventType, task);
            } catch (RuntimeException e) {
                // A listener failure must not turn an already committed change into a failed request.
                System.getLogger(TaskService.class.getName()).log(System.Logger.Level.WARNING, "Task listener failed", e);
            }
        }
    }

    private Task saveCreated(Task task) {
        Task saved = repository.save(task);
        notifyListeners("created", saved);
        return saved;
    }

    public Task createSubtask(int parentId, TaskRequest request) {
        if (getTask(parentId).getParentId() != null) {
            throw new ValidationException("parentId", "a subtask cannot have subtasks");
        }
        return saveCreated(validateFields(request).parentId(parentId).build());
    }

    public List<Task> listSubtasks(int parentId) {
        getTask(parentId);
        return repository.findAll().stream().filter(task -> Objects.equals(task.getParentId(), parentId)).toList();
    }

    public List<Task> breakDownTask(int id) {
        Task parent = getTask(id);
        if (parent.getParentId() != null) throw new ValidationException("id", "a subtask cannot be broken down");
        List<TaskRequest> requests = breakdown.breakDown(parent);
        if (requests == null || requests.isEmpty()) throw new BadGatewayException("No subtasks returned");
        List<Task> validated = new ArrayList<>();
        try {
            for (TaskRequest request : requests) validated.add(validateFields(request).parentId(id).build());
        } catch (ValidationException e) {
            throw new BadGatewayException("Model output failed validation", e.getIssues());
        }
        // No writes until every proposed subtask has passed validation.
        return validated.stream().map(this::saveCreated).toList();
    }

    public Task createTaskFromText(String text) {
        if (text == null || text.isBlank()) throw new ValidationException("text", "must not be empty");
        ParsedTask parsed = parser.parse(text, DateHelper.today());
        if (parsed == null) throw new BadGatewayException("Missing parser output");
        TaskRequest request = new TaskRequest(parsed.title(), parsed.description(), "false", parsed.dueDate(), null);
        Task task;
        try {
            task = validateFields(request).build();
        } catch (ValidationException e) {
            throw new BadGatewayException("Model output failed validation", e.getIssues());
        }
        return saveCreated(task);
    }

    public Task createTask(TaskRequest request) {
        return saveCreated(validateFields(request).build());
    }

    public Task getTask(int id) {
        Task task = repository.findById(id);
        if (task == null) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return task;
    }

    public List<Task> listTasks() {
        return listTasks(null);
    }

    public List<Task> listTasks(TaskQuery query) {
        if (query == null) query = new TaskQuery.Builder().build();
        return query.apply(repository.findAll());
    }

    public List<Task> listByPriority(String level) {
        Priority priority;
        try {
            priority = Priority.valueOf(level == null ? "" : level);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("level", "must be LOW, MEDIUM or HIGH");
        }
        return repository.findAll().stream().filter(new PriorityFilter(priority)::matches).toList();
    }

    public Task updateTask(int id, TaskRequest request) {
        Task existing = getTask(id);
        Task updated = validateFields(request).id(id).parentId(existing.getParentId())
                .createdAt(existing.getCreatedAt()).build();
        Task saved = repository.save(updated);
        notifyListeners(!existing.isCompleted() && saved.isCompleted() ? "completed" : "updated", saved);
        return saved;
    }

    public void deleteTask(int id) {
        Task root = getTask(id);
        Map<Integer, List<Task>> children = new HashMap<>();
        for (Task task : repository.findAll()) {
            if (task.getParentId() != null) {
                children.computeIfAbsent(task.getParentId(), key -> new ArrayList<>()).add(task);
            }
        }
        // Walk the saved relationships so nested subtasks are removed too.
        ArrayDeque<Task> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Task current = pending.remove();
            if (repository.delete(current.getId())) {
                notifyListeners("deleted", current);
                pending.addAll(children.getOrDefault(current.getId(), List.of()));
            }
        }
    }

    private static Task.TaskBuilder validateFields(TaskRequest request) {
        if (request == null) {
            throw new ValidationException("request", "must not be null");
        }
        List<FieldIssue> issues = new ArrayList<>();
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            issues.add(new FieldIssue("title", "must not be empty"));
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            issues.add(new FieldIssue("description", "must not be empty"));
        }
        String completed = request.getCompleted();
        if (!"true".equals(completed) && !"false".equals(completed)) {
            issues.add(new FieldIssue("completed", "must be true or false"));
        }
        if (request.getDueDate() != null && !DateHelper.isValidDate(request.getDueDate())) {
            issues.add(new FieldIssue("dueDate", "must be a real date in YYYY-MM-DD format"));
        }
        Priority priority = Priority.MEDIUM;
        if (request.getPriority() != null) {
            try {
                priority = Priority.valueOf(request.getPriority());
            } catch (IllegalArgumentException e) {
                issues.add(new FieldIssue("priority", "must be LOW, MEDIUM or HIGH"));
            }
        }
        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }
        return new Task.TaskBuilder(request.getTitle(), request.getDescription(), "true".equals(completed))
                .dueDate(request.getDueDate()).priority(priority);
    }
}
