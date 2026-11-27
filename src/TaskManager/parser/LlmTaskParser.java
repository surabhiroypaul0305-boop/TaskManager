package TaskManager.parser;

import TaskManager.exception.BadGatewayException;
import java.io.IOException;
import java.util.Map;

public final class LlmTaskParser implements TaskParser {
    private final LlmClient client;

    public LlmTaskParser(LlmClient client) { this.client = client; }

    @Override
    public ParsedTask parse(String text, String today) {
        String prompt = "Today is " + today + ". Extract one task from the input. Resolve relative dates. "
                + "Reply with only JSON: title and description are nonblank strings, dueDate is a YYYY-MM-DD "
                + "string or null, and recurrence is an optional string or null. Input: " + SimpleJson.quote(text);
        try {
            Map<String, String> fields = SimpleJson.parseStringFields(client.send(prompt));
            return new ParsedTask(fields.get("title"), fields.get("description"),
                    fields.get("dueDate"), fields.get("recurrence"));
        } catch (IOException e) {
            throw new BadGatewayException("Model call failed");
        } catch (RuntimeException e) {
            throw new BadGatewayException("Invalid model response");
        }
    }
}
