package TaskManager.event;

import TaskManager.model.Task;

public final class ConsoleLogListener implements TaskEventListener {
    @Override
    public void onEvent(String eventType, Task task) {
        System.out.printf("Task %d: %s%n", task.getId(), eventType);
    }
}
