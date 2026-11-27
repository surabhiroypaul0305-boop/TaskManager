package TaskManager.app;

import TaskManager.parser.SimpleJson;
import TaskManager.service.DateHelper;

import java.util.Map;

/**
 * INSTRUCTOR WALKTHROUGH (Step 4) - runs offline, no key needed.
 * Shows the three ideas of the AI step in ~10 minutes:
 *   1. read the API key from an environment variable (and never print it)
 *   2. prompt for structured JSON, with today's date inside the prompt
 *   3. NEVER TRUST THE MODEL - parse and validate before anything is saved
 */
public class PromptAndEnvWalkthrough {

    public static void main(String[] args) {
        // 1. API key from the environment. Set it in IntelliJ: Run > Edit Configurations > Environment variables.
        String key = System.getenv("LLM_API_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("1. LLM_API_KEY missing -> TaskApi must return 503 in the error shape (not crash)");
        } else {
            System.out.println("1. LLM_API_KEY present (" + key.length() + " chars) - never print the key itself");
        }

        // 2. A prompt that pins the output format and gives the model today's date.
        String today = DateHelper.today();
        String userText = "Remind me to pay rent on the 1st of every month";
        String prompt = "Today's date is " + today + " (format YYYY-MM-DD).\n"
                + "Turn the user's text into ONE task.\n"
                + "Reply with ONLY a JSON object, no explanation, no code fences, with exactly these fields:\n"
                + "  \"title\": short string, required\n"
                + "  \"description\": string, required\n"
                + "  \"dueDate\": YYYY-MM-DD or null\n"
                + "  \"recurrence\": string such as \"monthly\", or null\n"
                + "User text: " + userText;
        System.out.println("2. Prompt sent to the model:\n" + prompt + "\n");

        // 3. Models misbehave. Same text, three kinds of reply:
        tryReply("good reply",
                "{\"title\":\"Pay rent\",\"description\":\"Monthly rent\",\"dueDate\":\"2026-12-01\",\"recurrence\":\"monthly\"}");
        tryReply("fenced reply (parser tolerates it)",
                "```json\n{\"title\":\"Pay rent\",\"description\":\"Monthly rent\",\"dueDate\":null,\"recurrence\":null}\n```");
        tryReply("impossible date", "{\"title\":\"Pay rent\",\"description\":\"x\",\"dueDate\":\"2026-02-30\",\"recurrence\":null}");
        tryReply("empty title", "{\"title\":\"  \",\"description\":\"x\",\"dueDate\":null,\"recurrence\":null}");
        tryReply("not JSON at all", "Sure! Here is your task: pay rent on the 1st.");
    }

    private static void tryReply(String label, String reply) {
        System.out.println("3. " + label);
        Map<String, String> fields;
        try {
            fields = SimpleJson.parseFlat(reply);
        } catch (IllegalArgumentException e) {
            System.out.println("   REJECT (malformed): " + e.getMessage() + "  -> 502, save nothing");
            return;
        }
        String title = fields.get("title");
        String dueDate = fields.get("dueDate");
        if (title == null || title.isBlank()) {
            System.out.println("   REJECT: title must not be empty -> 502, save nothing");
        } else if (dueDate != null && !DateHelper.isValidDate(dueDate)) {
            System.out.println("   REJECT: dueDate '" + dueDate + "' is not a real date -> 502, save nothing");
        } else {
            System.out.println("   ACCEPT -> run through the SAME validation as createTask, then save: " + fields);
        }
    }
}
