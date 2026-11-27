package TaskManager.event;

import TaskManager.model.Task;

/** Receives the saved task, or its last snapshot after deletion. */
@FunctionalInterface
public interface TaskEventListener {
    void onEvent(String eventType, Task task);
}
