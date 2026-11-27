package TaskManager.api;

import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import TaskManager.service.TaskService;
import TaskManager.exception.ApiException;
import TaskManager.query.TaskQuery;
import TaskManager.repository.IdempotencyStore;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public class TaskApi {
    private final TaskService service;

    public TaskApi(TaskService service) {
        this.service = service;
    }

    public ApiResponse createTask(TaskRequest request, String idempotencyKey) {
        String fingerprint = request == null ? "createTask:null" : fingerprint("createTask", request.getTitle(),
                request.getDescription(), request.getCompleted(), request.getDueDate(), request.getPriority());
        return create(idempotencyKey, fingerprint, () -> Collections.unmodifiableMap(service.createTask(request).toMap()));
    }

    public ApiResponse createTaskFromText(String text, String idempotencyKey) {
        return create(idempotencyKey, fingerprint("createTaskFromText", text),
                () -> Collections.unmodifiableMap(service.createTaskFromText(text).toMap()));
    }

    public ApiResponse getTask(int id) {
        return respond(200, () -> service.getTask(id).toMap());
    }

    public ApiResponse listTasks() {
        return respond(200, () -> service.listTasks().stream().map(Task::toMap).toList());
    }

    public ApiResponse listTasks(TaskQuery query) {
        return respond(200, () -> service.listTasks(query).stream().map(Task::toMap).toList());
    }

    public ApiResponse listByPriority(String level) {
        return respond(200, () -> service.listByPriority(level).stream().map(Task::toMap).toList());
    }

    public ApiResponse updateTask(int id, TaskRequest request) {
        return respond(200, () -> service.updateTask(id, request).toMap());
    }

    public ApiResponse createSubtask(int parentId, TaskRequest request) {
        return respond(201, () -> service.createSubtask(parentId, request).toMap());
    }

    public ApiResponse listSubtasks(int parentId) {
        return respond(200, () -> service.listSubtasks(parentId).stream().map(Task::toMap).toList());
    }

    public ApiResponse breakDownTask(int id, String idempotencyKey) {
        return create(idempotencyKey, fingerprint("breakDownTask", Integer.toString(id)),
                () -> service.breakDownTask(id).stream().map(task -> Collections.unmodifiableMap(task.toMap())).toList());
    }

    public ApiResponse deleteTask(int id) {
        return respond(200, () -> {
            service.deleteTask(id);
            return Map.of("deleted", id);
        });
    }

    private static String fingerprint(String operation, String... fields) {
        StringBuilder result = new StringBuilder(operation).append(';');
        for (String field : fields) {
            if (field == null) result.append("-1:");
            else result.append(field.length()).append(':').append(field);
        }
        return result.toString();
    }

    private ApiResponse create(String key, String fingerprint, Supplier<Object> operation) {
        return handle(() -> IdempotencyStore.getInstance().execute(key, fingerprint,
                () -> new ApiResponse(201, operation.get())));
    }

    private ApiResponse respond(int status, Supplier<Object> operation) {
        return handle(() -> new ApiResponse(status, operation.get()));
    }

    private ApiResponse handle(Supplier<ApiResponse> operation) {
        try {
            return operation.get();
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.fromUnexpected(e);
        }
    }
}
