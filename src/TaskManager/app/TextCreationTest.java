package TaskManager.app;

import TaskManager.api.*;
import TaskManager.parser.*;
import TaskManager.repository.InMemoryTaskRepository;
import TaskManager.service.*;
import java.net.http.HttpTimeoutException;
import java.util.Map;

/** Offline integration checks: clients are injected, never contacted. */
public final class TextCreationTest {
    private static final InMemoryTaskRepository repo = InMemoryTaskRepository.getInstance();
    private static TaskApi api(TaskParser parser) { return new TaskApi(new TaskService(repo, parser)); }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void fails(TaskParser parser, int status, String code) {
        int before = repo.findAll().size();
        ApiResponse r = api(parser).createTaskFromText("Pay rent", null);
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        check(r.getStatus() == status && code.equals(error.get("code")) && error.get("details") instanceof java.util.List,
                r.toJson());
        check(repo.findAll().size() == before, "Failed request saved a task");
    }

    public static void main(String[] args) {
        String good = "{\"title\":\"Rent\",\"description\":\"Pay landlord\",\"dueDate\":\"2028-02-29\",\"recurrence\":\"monthly\"}";
        TaskParser llm = new LlmTaskParser(prompt -> {
            check(prompt.contains(DateHelper.today()) && prompt.contains("only JSON") && prompt.contains("Pay rent"), "prompt");
            return good;
        });
        ApiResponse created = api(llm).createTaskFromText("Pay rent", null);
        check(created.getStatus() == 201 && repo.findAll().size() == 1, "create");
        var saved = repo.findAll().get(0);
        check(saved.getTitle().equals("Rent") && saved.getDueDate().equals("2028-02-29") && !saved.isCompleted(), "saved fields");
        check(llm.parse("Pay rent", DateHelper.today()).recurrence().equals("monthly"), "recurrence");
        for (String bad : new String[]{"not json", good.replace("Rent", " "), good.replace("2028-02-29", "2026-02-30"),
                good.replace("\"Rent\"", "12"), good.replace("\"Pay landlord\"", "true"), good.replace("\"monthly\"", "[]")}) {
            fails(new LlmTaskParser(prompt -> bad), 502, "BAD_GATEWAY");
        }
        fails(new LlmTaskParser(prompt -> { throw new HttpTimeoutException("timeout"); }), 502, "BAD_GATEWAY");
        fails(ParserFactory.create(null, null, null, false), 503, "SERVICE_UNAVAILABLE");
        check(api(llm).createTaskFromText("  ", null).getStatus() == 400, "blank input");
        check(ParserFactory.create("unused", null, null, true) instanceof LlmTaskParser, "key selects LLM");
        TaskParser offline = ParserFactory.create(null, null, null, true);
        check(offline instanceof RuleBasedTaskParser, "offline strategy");
        check(offline.parse("Submit tomorrow", "2028-02-28").dueDate().equals("2028-02-29"), "tomorrow");
        ParsedTask rent = offline.parse("Pay on the 1st of every month", "2026-12-10");
        check(rent.dueDate().equals("2027-01-01") && rent.recurrence().equals("monthly"), "first of month");
        check(api(offline).createTaskFromText("Buy milk tomorrow", null).getStatus() == 201, "offline create");
        System.out.println("Text creation checks passed");
    }
}
