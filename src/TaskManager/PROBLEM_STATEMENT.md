# Task Manager Service

**Core Java Project Assignment (OOP, SOLID, Design Patterns and AI)**

---

## 1. Project Overview

**What will you build?** A task manager written in plain Java that uses an LLM to turn plain English, such as *"Remind me to pay rent on the 1st of every month"*, into structured tasks, and to break big tasks into subtasks. It supports subtasks, safe retries (idempotency), validation with one consistent error shape, filtering, sorting and pagination, and documentation.

The service works like a REST API without needing a web framework. Each operation is a Java method that returns an `ApiResponse` holding a status code (200, 201, 400, 404, 422, 502, 503) and a JSON-style body. You call these methods from a `Main` class. If the course later adds Spring Boot, the same service classes can sit behind real HTTP endpoints without changes. No framework is used.

**Before you start.** You need JDK 17 or newer, an IDE (IntelliJ IDEA or Cursor), a GitHub repository, and an API key for an LLM provider (Anthropic, OpenAI, or Gemini). No external libraries are required. The instructor supplies a small `LlmClient` class that handles the network call, so you work only with Java you have learned. Your tests use a fake client, so they need no key.

### Skills you will practice

- Classes, objects, constructors, and encapsulation (private fields, getters, validated state)
- Packages and access modifiers
- Custom exceptions, try/catch, and throws
- Interfaces and abstract classes, inheritance, polymorphism, and `@Override`
- SOLID principles, especially Single Responsibility, Open/Closed, and Dependency Inversion
- Design patterns: Builder, Singleton, Factory, Strategy, and Observer
- Idempotent operations using an in-memory store
- Using an AI assistant to generate tests, then critiquing them
- Writing a clear README and API contract

### How each requirement builds on what you learned in class

| Assignment item | Built from class topics |
|---|---|
| Immutable Task with required and optional fields | Constructors, private fields, getters, `this`, Builder pattern (pizza exercise), encapsulation |
| Validation rules | Validation inside setters and constructors (report card exercise), exceptions |
| One consistent error shape | Custom exception classes (`extends`), try/catch/throws, inheritance |
| Layers that can be swapped | Interfaces, abstract classes, SOLID (SRP, OCP, DIP) |
| AI task creation and breakdown | Strategy pattern, interface, polymorphism, Factory pattern, exceptions and the validation from Step 3, String methods |
| Single task store and idempotency store | Singleton pattern, static members, thread-safety discussion |
| Notifications on task events | Observer pattern (stock price example) |
| Filtering and sorting | Strategy pattern, interfaces, polymorphism |
| Project structure | Packages, public / private / default access |
| Testing | `main` method, interfaces for fakes (Strategy), IDE debugging (breakpoints), AI assistant workflow |

**Topics that need instructor-provided helpers:** List and Map, enum, date parsing, the HTTP call to the LLM, and reading JSON. These appear in the **Scaffold** boxes below. No framework and no test library is used.

---

## 2. Step-by-Step Brief

### Step 1: Project setup

1. Create a Java 17 project in your IDE named `task-manager`.
2. Create the packages: `model`, `service`, `repository`, `parser`, `event`, `exception`, `api`, and `app`.
3. Create `app/Main.java` with a `main` method that prints `"Task Manager started"`. Run it.
4. Add a `.gitignore` for `out/`, `.idea/`, and any `.env` file, then push the project to your GitHub repository.

### Step 2: Task model and CRUD

Create an immutable `Task` class. All fields are `private final`, there are no setters, and objects are created only through a **Builder** (`title`, `description`, and `completed` are required; the rest are optional).

| Field | Java type | Notes |
|---|---|---|
| `id` | `int` | Assigned by the repository, starting at 1 |
| `title` | `String` | Required |
| `description` | `String` | Required |
| `completed` | `boolean` | Required, default `false` |
| `dueDate` | `String` | Optional, format `YYYY-MM-DD` |
| `priority` | `Priority` (enum) | `LOW` / `MEDIUM` / `HIGH`, default `MEDIUM` |
| `parentId` | `Integer` | Optional, links a subtask to its parent |
| `createdAt` | `long` | Time the task was created (`System.currentTimeMillis()`) |

Then build these layers, each depending on **interfaces, not concrete classes** (Dependency Inversion):

- `TaskRepository` (interface) and `InMemoryTaskRepository` (implementation, written as a **Singleton**). It stores tasks in a collection and supports `save`, `findById`, `findAll`, and `delete`.
- `TaskService` with the business logic. It receives a `TaskRepository` through its constructor.
- `TaskApi` with the public operations below. It calls `TaskService` and always returns an `ApiResponse`.

| Operation (method on `TaskApi`) | Success | Notes |
|---|---|---|
| `createTask(TaskRequest request, String idempotencyKey)` | 201 | Returns the created task |
| `getTask(int id)` | 200 | 404 if the ID does not exist |
| `listTasks(TaskQuery query)` | 200 | Filtering and paging in Step 7 |
| `updateTask(int id, TaskRequest request)` | 200 | 404 if the ID does not exist |
| `deleteTask(int id)` | 200 | Also deletes the task's subtasks |

`TaskRequest` is a plain class that carries the raw input as `String` values (`title`, `description`, `completed`, `dueDate`, `priority`), so your validation decides what is acceptable. Test every operation from `Main` and print each `ApiResponse`.

> **Scaffold.** The instructor provides a short primer on List and Map (add, get, remove, loop) and on enum, plus the `ApiResponse` class with a `toJson()` helper. You write everything else.

### Step 3: Validation and one consistent error shape

Validate the following rules inside your **service layer**. Throw a custom exception when a rule fails.

- `title` and `description` must not be empty or blank.
- `completed` must be exactly `"true"` or `"false"`.
- `dueDate`, if given, must be a real `YYYY-MM-DD` date. `2026-02-30` must be rejected. Check the month and day yourself (remember leap years), or use the date helper from the instructor.
- `priority` must be `LOW`, `MEDIUM`, or `HIGH`.

Every error returned by `TaskApi` (400 for invalid input, 404 for an unknown ID) must use **exactly** this shape. The `code` and `message` values must always be strings.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [{ "field": "title", "issue": "must not be empty" }]
  }
}
```

How to build it from what you know:

- Create a custom exception hierarchy: a base `ApiException extends RuntimeException` holding a status code and an error code, and subclasses `ValidationException` (400), `NotFoundException` (404), `ConflictException` (422), `BadGatewayException` (502), and `ServiceUnavailableException` (503).
- `ValidationException` collects a list of field issues, so one request can report several problems at once.
- In `TaskApi`, wrap each operation in try/catch. Catch `ApiException` and convert it into an `ApiResponse` with the error shape. Catch any other `Exception` and return a 500 in the same shape.
- Use a small Factory method such as `ErrorResponseFactory.from(ApiException e)` so the shape is built in one place.

> **Scaffold.** The instructor provides the class skeletons and constructors for the exception hierarchy and the `ErrorResponseFactory` signature. You write the logic.

### Step 4: AI-native task creation

Add `createTaskFromText(String text, String idempotencyKey)`. It sends the text to an LLM and turns the answer into a task.

1. Define an interface `TaskParser` with one method: `ParsedTask parse(String text, String today)`. `ParsedTask` holds `title`, `description`, `dueDate`, and an optional `recurrence`.
2. Write `LlmTaskParser implements TaskParser`. It builds a prompt that includes **today's date** (so "next Friday" can be resolved), asks the model to reply with **only JSON** containing those fields, and calls the `LlmClient` provided by the instructor.
3. Read the model's reply with the `SimpleJson` helper and build a `ParsedTask`.
4. Convert the `ParsedTask` into a `TaskRequest` and run it through the **same validation as Step 3**. This is the *"never trust the model"* rule. If validation fails (empty title, impossible date, wrong type), return the error shape with status **502** and save nothing.
5. If the model call fails or times out, return **502** as well.
6. Keep a simple `RuleBasedTaskParser implements TaskParser` for offline work (handle "tomorrow", "every month", "1st" with String methods). A `ParserFactory` returns the LLM parser when a key is present and lets you choose the rule-based one otherwise. This is the **Strategy** pattern: the service does not care which parser it receives.
7. Document the `TaskParser` interface in the README so another parser can be plugged in.

> **Scaffold.** The instructor provides `LlmClient` (one method that sends a prompt `String` and returns the model's reply `String`, with a timeout) and `SimpleJson` (reads flat JSON fields into a `Map`). You write the prompt, the parser class, and the validation.

> **API key rule.** Read the key with `System.getenv("LLM_API_KEY")`. If it is missing, return **503** in your error shape instead of crashing. **Never commit a key.**

### Step 5: Subtasks and breakdown

- `createSubtask(int parentId, TaskRequest request)` creates a task with `parentId` set and returns 201.
- `listSubtasks(int parentId)` returns the subtasks of a task.
- `breakDownTask(int id, String idempotencyKey)` uses a `BreakdownStrategy` interface to produce a list of subtask requests. Write `LlmBreakdownStrategy`, which asks the model for subtasks in a fixed format (for example one per line as `title|description`) and parses the reply with String methods. Keep a `RuleBasedBreakdownStrategy` (split on "and" or commas) for offline work.
- Validate **every** item the model returns first. If any item is invalid, or the model call fails, save **none** of them and return the error shape (502).
- A task that is itself a subtask cannot be broken down or given subtasks. Return 400.
- Deleting a parent deletes its subtasks. Print the repository contents before and after to prove it.

### Step 6: Idempotent creation

Networks fail and clients retry, so a retry must never create a duplicate. Add an optional `idempotencyKey` parameter (it can be `null`) to `createTask`, `createTaskFromText`, and `breakDownTask`.

- Create an `IdempotencyStore` as a **Singleton**. Inside, keep a `Map` from key to a stored record holding the request **fingerprint** and the **response**.
- Build the fingerprint from the request contents (for example, all field values joined into one String).
- **Same key and same request:** return the stored response. Create nothing and do not call the parser again.
- **Same key with a different request:** return **422** in your error shape.
- Store **only successful** responses, so a client can fix bad input and retry with the same key.
- If you use threads anywhere, make the store thread-safe (see the Singleton and `synchronized` discussion from class).
- **Prove it:** call the same operation twice with the same key and print the repository to show that only one task exists.

> **Scaffold.** The instructor provides a short outline of the check-then-store flow. You implement it.

### Step 7: Filtering, search, sorting, and pagination

Build a `TaskQuery` object with the **Builder** pattern. All its fields are optional, which is a good fit for Builder.

| Feature | Behaviour |
|---|---|
| `completed` | Return only completed or only open tasks |
| `priority` | Return only tasks with that priority |
| `search` | Case-insensitive text match on title or description |
| `sortBy` and `order` | Sort by `createdAt` or `dueDate`, ascending or descending |
| `page` and `pageSize` | Page size 10 by default; page 1 is the first page |
| `listByPriority(String level)` | Returns tasks of one priority; 400 for an invalid level |

Implement each filter as a class that implements a `TaskFilter` interface, and each sort order as a class that implements a `SortStrategy` interface. Adding a new filter or sort should **not** require editing existing classes (Open/Closed principle). Confirm that filtering, search, sorting, and pagination work **together** in one call.

### Step 8: Events with the Observer pattern

Make the service announce what happens, so other parts of the program can react without changing the service.

- Define an interface `TaskEventListener` with a method such as `onEvent(String eventType, Task task)`.
- Make `TaskService` the subject: it keeps a list of listeners, with `addListener` and `removeListener`, and notifies them after a task is created, updated, completed, or deleted.
- Write at least two listeners, for example `ConsoleLogListener` and `AuditTrailListener` that keeps a count of each event type.
- Show in `Main` that removing a listener stops its notifications.

### Step 9: Testing with AI, then hardening

You already use an AI assistant (Cursor, ChatGPT, or similar). Use the same workflow:

1. Ask the assistant to generate a **test checklist** and a `TestRunner` class that calls your `TaskApi` methods and prints `PASS` or `FAIL` for each case. Tests must pass **without a real API key or internet**.
2. Run it. Use IDE breakpoints to inspect the parser and breakdown output.
3. **Critique** the result. Look for missing cases: empty or invalid input, unknown IDs, a repeated idempotency key, the same key with a different request, impossible dates, bad parser output (empty title), a subtask of a subtask, and page numbers beyond the last page.
4. Add or fix cases to close the gaps.
5. Replace the real model with **fakes**: write a `FakeLlmClient` that returns a fixed reply, and a `FaultyLlmClient` that returns bad output (empty title, impossible date, malformed text) or throws. Plug them in through the Factory. Confirm that bad output gives 502 and no task is saved, and that a repeated idempotency key never calls the fake twice.

> **Scaffold.** The instructor provides a small example `TestRunner` with one finished check (call, compare, print PASS or FAIL) as a pattern to follow.

### Step 10: Documentation

Your `README.md` must contain:

- An overview of the project and its package structure.
- Setup instructions: JDK version and how to compile and run `Main`, and how to set `LLM_API_KEY`.
- A table of every `TaskApi` operation with its parameters, success status, and error statuses, plus an example call and printed response for each, including the AI task-creation and breakdown operations and a validation error.
- A list of the design patterns you used and where (Builder, Singleton, Factory, Strategy, Observer).
- The prompts you wrote for task creation and breakdown, and why you wrote them that way.
- A short note on what the AI-generated tests missed and what you changed.

Also add `API_CONTRACT.md`, written by hand, describing each operation and the error response (the error shape, every error code, and its status). If you later expose the service over HTTP, this becomes your OpenAPI file.

> **Scaffold.** The instructor provides a template for `API_CONTRACT.md` with one finished operation and the error section.

---

## 3. Submission

Push your code to your GitHub repository and submit the repository link. A pull request is optional. Before submitting, confirm that the project compiles from a clean checkout, `Main` and `TestRunner` run, the README renders correctly, and no API key or `.env` file is committed.

---

## 4. Grading Checklist

- [ ] Immutable `Task` created only through a Builder
- [ ] CRUD works with correct 200, 201, 400, and 404 results
- [ ] All validation rules enforced, including the impossible date
- [ ] Every error uses the one consistent shape, built from a custom exception hierarchy
- [ ] `createTaskFromText` calls the LLM, validates its output, and returns 502 on failure (503 when the key is missing) without saving
- [ ] AI breakdown is all-or-nothing and blocks sub-subtasks
- [ ] Idempotency: replay returns the original, a different request returns 422, failures are not remembered
- [ ] Filter, search, sort, and pagination work together and are open for extension (interfaces)
- [ ] Observer notifications work, including removing a listener
- [ ] Layers depend on interfaces (Dependency Inversion) and packages use sensible access modifiers
- [ ] `TestRunner` passes offline using fake clients; written checklist and the "what the AI missed" note are included
- [ ] README and `API_CONTRACT.md` complete; no key committed

---

> **Upgrade path.** After Spring Boot is taught, learners can wrap `TaskApi` in `@RestController` classes, replace `InMemoryTaskRepository` with a JPA repository, and replace `ApiResponse` with `ResponseEntity`.
