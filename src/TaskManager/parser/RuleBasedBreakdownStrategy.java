package TaskManager.parser;

import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import java.util.Arrays;
import java.util.List;

public final class RuleBasedBreakdownStrategy implements BreakdownStrategy {
    @Override
    public List<TaskRequest> breakDown(Task parent) {
        String separator = "(?i)\\band\\b|,";
        String[] parts = parent.getDescription().split(separator);
        if (parts.length <= 1) parts = parent.getTitle().split(separator);
        return Arrays.stream(parts).map(String::strip).filter(part -> !part.isEmpty())
                .map(part -> new TaskRequest(part, part, "false", null, null)).toList();
    }
}
