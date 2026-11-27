# Creating tasks from text

`TaskApi.createTaskFromText(text, idempotencyKey)` returns 201 with the saved task. A non-null key replays the original successful response for the same operation and input; reusing it with different input or another operation returns 422. Failed attempts do not reserve the key. Keys are held in memory until the process exits.

`TaskManager.parser.TaskParser` defines `ParsedTask parse(String text, String today)`. Implementations get nonblank input and today's ISO date. Return a `ParsedTask` with a nonblank title and description, a real ISO due date or null, and a recurrence string or null. Recurrence is parser metadata, not a stored `Task` field. Parsers must not save tasks. The service validates the result and saves it only after validation succeeds. Provider or parsing failures should throw `BadGatewayException` (502); invalid caller input returns 400.

Inject any implementation through the service constructor. For example, this uses no network:

```java
TaskParser parser = new RuleBasedTaskParser();
TaskApi api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(), parser));
System.out.println(api.createTaskFromText("Pay rent on the 1st of every month", null));
```

The offline parser recognizes tomorrow, the next 1st, and monthly recurrence. To plug in another parser, implement `TaskParser` and pass it in place of `RuleBasedTaskParser` above.

The default service constructor uses `ParserFactory.fromEnvironment(false)`: it selects the instructor's HTTP client when `LLM_API_KEY` is present (optional `LLM_PROVIDER` and `LLM_MODEL`), and returns 503 on text creation without a key. Pass `ParserFactory.fromEnvironment(true)` to opt into offline fallback when the key is absent. With a key, text creation sends the supplied text and today's date to the configured provider. Use `new RuleBasedTaskParser()` explicitly to stay offline even with a key.
