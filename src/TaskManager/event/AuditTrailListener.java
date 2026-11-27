package TaskManager.event;

import TaskManager.model.Task;
import java.util.HashMap;
import java.util.Map;

public final class AuditTrailListener implements TaskEventListener {
    private final Map<String, Integer> counts = new HashMap<>();

    @Override
    public synchronized void onEvent(String eventType, Task task) {
        counts.merge(eventType, 1, Integer::sum);
    }

    public synchronized int count(String eventType) {
        return counts.getOrDefault(eventType, 0);
    }
}
