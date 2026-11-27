package TaskManager.repository;

import TaskManager.model.Task;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class InMemoryTaskRepository implements TaskRepository {
    private static final InMemoryTaskRepository INSTANCE = new InMemoryTaskRepository();
    private final Map<Integer, Task> tasks = new TreeMap<>();
    private int nextId = 1;

    private InMemoryTaskRepository() {}

    public static InMemoryTaskRepository getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized Task save(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task should not be empty");
        }
        if (task.getId() == 0) {
            // Skip any IDs supplied explicitly by a caller.
            while (tasks.containsKey(nextId)) {
                nextId++;
            }
            task = task.toBuilder().id(nextId++).build();
        }
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public synchronized Task findById(int id) {
        return tasks.get(id);
    }

    @Override
    public synchronized List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public synchronized boolean delete(int id) {
        return tasks.remove(id) != null;
    }
}
