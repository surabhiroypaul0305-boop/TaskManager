# Task Manager Service

A task manager in plain Java 17 with no framework and no external libraries. It works like a REST API: every operation is a method on `TaskApi` that returns an `ApiResponse` holding a status code and a JSON-style body. An LLM turns plain English such as *"Remind me to pay rent on the 1st of every month"* into a task, and splits a task into subtasks.

Features: an immutable `Task` built through a Builder, validation with one error shape, subtasks, idempotent creation, filtering, search, sorting and pagination, and event notifications.

The full contract for each operation is in [API_CONTRACT.md](API_CONTRACT.md).

## Package structure

All code is under `src/TaskManager/`.

| Package | Contents |
|---|---|
| `model` | `Task` (immutable, with `TaskBuilder`), `TaskRequest` (raw `String` input), `Priority` enum |
| `repository` | `TaskRepository` interface, `InMemoryTaskRepository` (Singleton), `IdempotencyStore` (Singleton) |
| `service` | `TaskService` (business logic, validation, event subject), `DateHelper` |
| `parser` | `TaskParser` and `BreakdownStrategy` interfaces with LLM and rule-based implementations, `ParserFactory`, `LlmClient`, `HttpLlmClient`, `SimpleJson` |
| `query` | `TaskQuery` (Builder), `TaskFilter` and `SortStrategy` interfaces and their implementations |
| `event` | `TaskEventListener`, `ConsoleLogListener`, `AuditTrailListener` |
| `exception` | `ApiException` and its subclasses, `FieldIssue` |
| `api` | `TaskApi`, `ApiResponse`, `ErrorResponseFactory` |
| `testsupport` | `FakeLlmClient` and `FaultyLlmClient` for offline tests |
| `app` | `Main`, `TestRunner` and the other runnable checks |
| `primer` | Instructor's List/Map/enum primer |

Dependencies point inward through interfaces: `TaskApi` → `TaskService` → `TaskRepository`, `TaskParser`, `BreakdownStrategy` → `LlmClient`.

## Setup

**JDK:** 17 or newer (the code uses records, switch expressions and pattern matching for `instanceof`). Tested with OpenJDK 21 using `--release 17`.

**Compile and run `Main`** from the project root:

```sh
javac -d out src/TaskManager/*/*.java
java -cp out TaskManager.app.Main
```

**Run the offline tests:**

```sh
java -cp out TaskManager.app.TestRunner
```

To compile once and run every entry point, each in its own JVM so the singletons start empty, run `bash src/TaskManager/run-all.sh`. It unsets `LLM_API_KEY` first, so no live provider is called.

In IntelliJ, mark `src` as the sources root and run `TaskManager.app.Main`.

`Main` builds its service with `RuleBasedTaskParser` and `RuleBasedBreakdownStrategy`, so it runs offline and never calls the LLM, even when a key is set.

### Setting `LLM_API_KEY`

The key is read with `System.getenv("LLM_API_KEY")`. Never commit it, and keep any `.env` file out of Git.

| Variable | Required | Meaning |
|---|---|---|
| `LLM_API_KEY` | for AI operations | Provider API key |
| `LLM_PROVIDER` | no | `anthropic` (default), `openai` or `gemini` |
| `LLM_MODEL` | no | Overrides the default model. Set it if the provider answers HTTP 400 or 404 for the default name |

Shell (macOS/Linux):

```sh
export LLM_API_KEY='your-key'
export LLM_PROVIDER=anthropic
java -cp out TaskManager.app.LlmSmokeTest   # prints "Model replied: OK" when the setup works
```

IntelliJ: **Run > Edit Configurations > Environment variables**, then add `LLM_API_KEY=your-key`.

If the key is missing, CRUD still works. `createTaskFromText` and `breakDownTask` return `503 SERVICE_UNAVAILABLE` instead of crashing.

## TaskApi operations

| Operation | Parameters | Success | Errors |
|---|---|---|---|
| `createTask` | `TaskRequest request`, `String idempotencyKey` (nullable) | 201 task | 400, 422, 500 |
| `getTask` | `int id` | 200 task | 404, 500 |
| `listTasks` | `TaskQuery query` (nullable). A no-argument overload lists everything | 200 list | 400, 500 |
| `updateTask` | `int id`, `TaskRequest request` | 200 task | 400, 404, 500 |
| `deleteTask` | `int id` | 200 `{"deleted": id}` | 404, 500 |
| `createTaskFromText` | `String text`, `String idempotencyKey` (nullable) | 201 task | 400, 422, 502, 503, 500 |
| `createSubtask` | `int parentId`, `TaskRequest request` | 201 task | 400, 404, 500 |
| `listSubtasks` | `int parentId` | 200 list | 404, 500 |
| `breakDownTask` | `int id`, `String idempotencyKey` (nullable) | 201 list | 400, 404, 422, 502, 503, 500 |
| `listByPriority` | `String level` | 200 list | 400, 500 |

`TaskRequest` fields are all `String`: `title` and `description` (required, not blank), `completed` (exactly `"true"` or `"false"`), `dueDate` (optional, a real `YYYY-MM-DD` date), and `priority` (optional, `LOW`/`MEDIUM`/`HIGH`, default `MEDIUM`).

### Examples

The responses below are real output from `System.out.println(response)`, which calls `ApiResponse.toJson()`. They were produced offline: `FakeLlmClient` stands in for the model, so the AI operations show what the LLM path does with a well-formed reply. Error bodies are built with `Map.of`, so the order of `code`, `message` and `details` in printed output can vary between runs. `createdAt` is the creation time in milliseconds.

Setup used for the examples:

```java
var api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
        ParserFactory.create(new FakeLlmClient("{\"title\":\"Pay rent\",\"description\":\"Pay rent on the 1st of every month\",\"dueDate\":\"2026-09-01\",\"recurrence\":\"monthly\"}")),
        ParserFactory.createBreakdown(new FakeLlmClient("Find bank details|Look up the landlord's account\nTransfer money|Send the rent payment"))));
```

**createTask**

```java
api.createTask(new TaskRequest("Write report", "Quarterly sales report", "false", "2026-12-01", "HIGH"), "report-001");
```
```
{"status":201,"body":{"id":1,"title":"Write report","description":"Quarterly sales report","completed":false,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795783545916}}
```

The same call again with key `report-001` prints the identical response and creates nothing. Reusing the key with a different request:

```
{"status":422,"body":{"error":{"message":"Idempotency key reused with a different request","code":"CONFLICT","details":[]}}}
```

**Validation error** (every broken rule is reported at once):

```java
api.createTask(new TaskRequest("", "  ", "yes", "2026-02-30", "URGENT"), null);
```
```
{"status":400,"body":{"error":{"message":"Validation failed","code":"VALIDATION_ERROR","details":[{"issue":"must not be empty","field":"title"},{"issue":"must not be empty","field":"description"},{"issue":"must be true or false","field":"completed"},{"issue":"must be a real date in YYYY-MM-DD format","field":"dueDate"},{"issue":"must be LOW, MEDIUM or HIGH","field":"priority"}]}}}
```

**getTask**

```java
api.getTask(1);
api.getTask(99);
```
```
{"status":200,"body":{"id":1,"title":"Write report","description":"Quarterly sales report","completed":false,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795783545916}}
{"status":404,"body":{"error":{"message":"Task 99 not found","code":"NOT_FOUND","details":[]}}}
```

**createTaskFromText** (AI task creation)

```java
api.createTaskFromText("Remind me to pay rent on the 1st of every month", "text-001");
```
```
{"status":201,"body":{"id":2,"title":"Pay rent","description":"Pay rent on the 1st of every month","completed":false,"dueDate":"2026-09-01","priority":"MEDIUM","parentId":null,"createdAt":1795783546061}}
```

When the model returns an empty title, the output fails validation and nothing is saved:

```
{"status":502,"body":{"error":{"message":"Model output failed validation","code":"BAD_GATEWAY","details":[{"issue":"must not be empty","field":"title"}]}}}
```

Without `LLM_API_KEY`:

```
{"status":503,"body":{"error":{"message":"LLM_API_KEY is not configured","code":"SERVICE_UNAVAILABLE","details":[]}}}
```

**createSubtask**

```java
api.createSubtask(1, new TaskRequest("Collect numbers", "Pull sales data", "false", null, null));
api.createSubtask(3, new TaskRequest("Nested", "x", "false", null, null));   // 3 is a subtask
```
```
{"status":201,"body":{"id":3,"title":"Collect numbers","description":"Pull sales data","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":1,"createdAt":1795783546069}}
{"status":400,"body":{"error":{"message":"Validation failed","code":"VALIDATION_ERROR","details":[{"issue":"a subtask cannot have subtasks","field":"parentId"}]}}}
```

**listSubtasks**

```java
api.listSubtasks(1);
```
```
{"status":200,"body":[{"id":3,"title":"Collect numbers","description":"Pull sales data","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":1,"createdAt":1795783546069}]}
```

**breakDownTask** (AI breakdown)

```java
api.breakDownTask(2, "break-001");
```
```
{"status":201,"body":[{"id":4,"title":"Find bank details","description":"Look up the landlord's account","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795783546084},{"id":5,"title":"Transfer money","description":"Send the rent payment","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795783546084}]}
```

When the model returns `A|ok` and `B|` (the second item has no description), neither subtask is saved:

```
{"status":502,"body":{"error":{"message":"Model output failed validation","code":"BAD_GATEWAY","details":[{"issue":"must not be empty","field":"description"}]}}}
```

**listTasks** (filter, search, sort and paginate in one call)

```java
api.listTasks(new TaskQuery.Builder().completed(false).search("rent")
        .sortBy("dueDate").order("asc").page(1).pageSize(2).build());
api.listTasks(new TaskQuery.Builder().page(0).build());
```
```
{"status":200,"body":[{"id":2,"title":"Pay rent","description":"Pay rent on the 1st of every month","completed":false,"dueDate":"2026-09-01","priority":"MEDIUM","parentId":null,"createdAt":1795783546061},{"id":5,"title":"Transfer money","description":"Send the rent payment","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795783546084}]}
{"status":400,"body":{"error":{"message":"Validation failed","code":"VALIDATION_ERROR","details":[{"issue":"must be 1 or greater","field":"page"}]}}}
```

Tasks without a `dueDate` sort last. A page past the last one returns `200` with `[]`.

**listByPriority**

```java
api.listByPriority("HIGH");
api.listByPriority("URGENT");
```
```
{"status":200,"body":[{"id":1,"title":"Write report","description":"Quarterly sales report","completed":false,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795783545916}]}
{"status":400,"body":{"error":{"message":"Validation failed","code":"VALIDATION_ERROR","details":[{"issue":"must be LOW, MEDIUM or HIGH","field":"level"}]}}}
```

**updateTask**

```java
api.updateTask(1, new TaskRequest("Write report", "Quarterly sales report", "true", "2026-12-01", "HIGH"));
```
```
{"status":200,"body":{"id":1,"title":"Write report","description":"Quarterly sales report","completed":true,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795783545916}}
```

**deleteTask** (also deletes the task's subtasks)

```java
api.deleteTask(2);   // task 2 has subtasks 4 and 5
api.deleteTask(99);
```
```
{"status":200,"body":{"deleted":2}}
{"status":404,"body":{"error":{"message":"Task 99 not found","code":"NOT_FOUND","details":[]}}}
```

The repository held 5 tasks before this delete and 2 after.

## Design patterns

| Pattern | Where | Why |
|---|---|---|
| Builder | `Task.TaskBuilder` in `model/Task.java`; `TaskQuery.Builder` in `query/TaskQuery.java` | `Task` is immutable with three required and several optional fields. Every `TaskQuery` field is optional |
| Singleton | `InMemoryTaskRepository.getInstance()`; `IdempotencyStore.getInstance()` | One task store and one key store per JVM. Both use a private constructor and an eagerly created instance, and synchronize their methods so check-then-store is atomic |
| Factory | `ParserFactory` (`create`, `createBreakdown`, `fromEnvironment`, `breakdownFromEnvironment`); `ErrorResponseFactory.from` / `fromUnexpected` | `ParserFactory` picks the LLM or rule-based implementation, or a 503 stub when the key is missing. `ErrorResponseFactory` builds the error shape in one place |
| Strategy | `TaskParser` (`LlmTaskParser`, `RuleBasedTaskParser`); `BreakdownStrategy` (`LlmBreakdownStrategy`, `RuleBasedBreakdownStrategy`); `TaskFilter` (`CompletedFilter`, `PriorityFilter`, `SearchFilter`); `SortStrategy` (`CreatedAtSort`, `DueDateSort`); `LlmClient` (`HttpLlmClient`, `FakeLlmClient`, `FaultyLlmClient`) | `TaskService` and `TaskQuery` work with whichever implementation they receive. New filters and sorts plug in through `TaskQuery.Builder.filter(...)` and `.sortStrategy(...)` without editing existing classes |
| Observer | `TaskService` is the subject (`addListener`, `removeListener`); `TaskEventListener` with `ConsoleLogListener` and `AuditTrailListener` | The service emits `created`, `updated`, `completed` and `deleted` without knowing who listens. A failing listener is logged and does not fail the request |

## Plugging in a parser

`TaskManager.parser.TaskParser` has one method:

```java
ParsedTask parse(String text, String today);
```

An implementation receives nonblank text and today's date as `YYYY-MM-DD`. It returns a `ParsedTask(title, description, dueDate, recurrence)`, where `dueDate` and `recurrence` may be null. It must not save anything: `TaskService` validates the result with the same rules as `createTask` and saves it only if validation passes. Throw `BadGatewayException` for provider or parsing failures.

Pass the implementation to the service constructor:

```java
TaskParser parser = new RuleBasedTaskParser();   // or your own implementation
TaskApi api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(), parser, new RuleBasedBreakdownStrategy()));
```

`RuleBasedTaskParser` works offline. It recognises "tomorrow", "1st" (the next first of the month) and "every month" (recurrence `monthly`). `BreakdownStrategy` (`List<TaskRequest> breakDown(Task parent)`) plugs in the same way. `RuleBasedBreakdownStrategy` splits the description, or the title, on "and" and commas.

The one-argument `TaskService(repository)` constructor calls `ParserFactory.fromEnvironment(false)`: it uses the LLM when `LLM_API_KEY` is set and otherwise returns 503 for text creation. `fromEnvironment(true)` falls back to `RuleBasedTaskParser` when no key is present.

## Prompts

### Task creation (`LlmTaskParser`)

```
Today is <today>. Extract one task from the input. Resolve relative dates. Reply with only JSON: title and
description are nonblank strings, dueDate is a YYYY-MM-DD string or null, and recurrence is an optional
string or null. Input: "<text, JSON-quoted>"
```

Why it is written this way:

- **Today's date comes first.** The model has no clock, so "tomorrow", "next Friday" or "the 1st" cannot be resolved without it. `DateHelper.today()` supplies it.
- **"Reply with only JSON".** `SimpleJson.parseStringFields` reads one flat object of strings and nulls. Any prose or extra structure makes it throw, and that becomes a 502.
- **Field rules match the validation rules.** "Nonblank", "YYYY-MM-DD" and "or null" are the checks `TaskService` runs on the result, so a well-behaved model passes them the first time. The service still validates every reply, because the model can ignore instructions.
- **"Extract one task".** The operation creates exactly one task, and the parser reads one object.
- **The user's text is JSON-quoted and placed last.** Quoting marks where the input starts and ends, so text such as `Ignore the above` reads as data rather than as part of the instructions. It also keeps newlines and quotes in the input from breaking the prompt.
- **No priority or completed field is requested.** Text-created tasks always start with `completed = "false"` and the default priority, so there is less for the model to get wrong.

### Breakdown (`LlmBreakdownStrategy`)

```
Split this task into subtasks. Reply only with one title|description per line. No headings, numbering, or
extra pipe characters.
Title: "<parent title, JSON-quoted>"
Description: "<parent description, JSON-quoted>"
```

Why it is written this way:

- **A line format instead of JSON.** The brief asks for `title|description` per line, which can be parsed with `lines()`, `indexOf('|')` and `strip()`.
- **"No headings, numbering, or extra pipe characters".** These are the ways models usually break a line format. The parser rejects any line without exactly one `|`, and the whole breakdown then fails with 502. Naming the failure modes in the prompt makes them less likely.
- **The parent's title and description are both quoted.** The model needs both to propose useful steps. Quoting keeps them separate from the instructions, as in the creation prompt.
- **All or nothing.** The service validates every returned item before saving any of them, so one bad line cannot leave a half-finished breakdown.

## Testing: what the AI-generated tests missed

`TestRunner` runs offline. `FakeLlmClient` returns a fixed reply and counts calls, and `FaultyLlmClient` returns an empty title, an impossible date or malformed text, or throws an `IOException`. Both are plugged in through `ParserFactory.create(...)` and `ParserFactory.createBreakdown(...)`. The full checklist is in [src/TaskManager/TEST_CHECKLIST.md](src/TaskManager/TEST_CHECKLIST.md).

The first AI-generated runner had 22 checks. They covered structured validation and the error shape, but missed most of the AI and idempotency paths:

- It built a `BadGatewayException` directly and checked the formatted body. That never proved the parser rejects a bad reply, or that the repository is unchanged afterwards.
- It had no test of a breakdown where one item is valid and the next is invalid. That case shows whether the save is all or nothing.
- It never checked that a replayed idempotency key skips the model. Only a call count shows that.
- It never retried a key after a failed attempt, so it could not show that failures are not stored.
- Subtask-of-a-subtask, breaking down a subtask, a page past the last one, and the sanitized 500 were covered only in separate suites, not through `TaskApi`.

What I changed: `TestRunner` now drives these flows through `TaskApi` with counting fakes. It snapshots the repository before each faulty call and compares afterwards, asserts call counts after replays and conflicts, retries the same key after a failure, and checks the full error body rather than only the status. It now runs 52 checks, all passing.
