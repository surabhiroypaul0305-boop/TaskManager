package TaskManager.parser;

import TaskManager.exception.BadGatewayException;
import TaskManager.model.Task;
import TaskManager.model.TaskRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class LlmBreakdownStrategy implements BreakdownStrategy {
    private final LlmClient client;

    public LlmBreakdownStrategy(LlmClient client) { this.client = client; }

    @Override
    public List<TaskRequest> breakDown(Task parent) {
        String prompt = "Split this task into subtasks. Reply only with one title|description per line. "
                + "No headings, numbering, or extra pipe characters.\nTitle: " + SimpleJson.quote(parent.getTitle())
                + "\nDescription: " + SimpleJson.quote(parent.getDescription());
        String reply;
        try {
            reply = client.send(prompt);
        } catch (IOException | RuntimeException e) {
            throw new BadGatewayException("Model call failed");
        }
        if (reply == null || reply.isBlank()) throw new BadGatewayException("Empty breakdown response");
        List<TaskRequest> requests = new ArrayList<>();
        for (String line : reply.strip().lines().toList()) {
            int separator = line.indexOf('|');
            if (separator < 0 || separator != line.lastIndexOf('|')) {
                throw new BadGatewayException("Expected one title|description pair per line");
            }
            requests.add(new TaskRequest(line.substring(0, separator).strip(), line.substring(separator + 1).strip(),
                    "false", null, null));
        }
        return requests;
    }
}
