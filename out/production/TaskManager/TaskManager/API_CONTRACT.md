# Task Manager API Contract


---

## Conventions

- Every `TaskApi` method returns an `ApiResponse`: a numeric **status** and a JSON-style **body**.
- Success bodies are a task object, a list of task objects, or a small confirmation object.
- Every failure body uses the single error shape in **Errors** below. `code` and `message` are always strings.
- `idempotencyKey` is optional (may be `null`). See each operation for how it behaves.

---

## Operations

### createTask — finished example

| | |
|---|---|
| **Java method** | `ApiResponse createTask(TaskRequest request, String idempotencyKey)` |
| **Purpose** | Create one task from structured input |
| **Success** | `201` — the created task |
| **Errors** | `400` VALIDATION_ERROR · `422` CONFLICT (key reused with a different request) · `500` INTERNAL_ERROR |

**Parameters**

| Name | Type | Required | Rules |
|---|---|---|---|
| `title` | String | yes | not empty or blank |
| `description` | String | yes | not empty or blank |
| `completed` | String | yes | exactly `"true"` or `"false"` |
| `dueDate` | String | no | a real calendar date, `YYYY-MM-DD` (`2026-02-30` is rejected) |
| `priority` | String | no | `LOW`, `MEDIUM` or `HIGH`; defaults to `MEDIUM` |
| `idempotencyKey` | String | no | same key + same request returns the original response, nothing new is created |

**Example request**

```
title = "Pay rent"   description = "Monthly rent"   completed = "false"
dueDate = "2026-12-01"   priority = "HIGH"   idempotencyKey = "rent-001"
```

**Example success response**

```json
{
  "status": 201,
  "body": {
    "id": 1, "title": "Pay rent", "description": "Monthly rent", "completed": false,
    "dueDate": "2026-12-01", "priority": "HIGH", "parentId": null, "createdAt": 1788000000000
  }
}
```

**Example error response** (`title = ""`)

```json
{
  "status": 400,
  "body": {
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Validation failed",
      "details": [{ "field": "title", "issue": "must not be empty" }]
    }
  }
}
```

### getTask
| | |
|---|---|
| **Java method** | `ApiResponse getTask(int id)` |
| **Purpose** | Fetch one task by id |
| **Success** | `200` — the task |
| **Errors** | `404` NOT_FOUND · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `id` | int | yes | id of an existing task |

**Example request**
```
id = 1
```
**Example success response**
```json
{ "status": 200, "body": { "id": 1, "title": "Pay rent", "description": "Monthly rent", "completed": false, "dueDate": "2026-12-01", "priority": "HIGH", "parentId": null, "createdAt": 1788000000000 } }
```
**Example error response** (`id = 99`)
```json
{
  "status": 404,
  "body": { "error": { "code": "NOT_FOUND", "message": "Task 99 not found", "details": [] } }
}
```

### listTasks
| | |
|---|---|
| **Java method** | `ApiResponse listTasks(TaskQuery query)` |
| **Purpose** | List tasks with optional filtering, search, sorting and pagination, all applied in one call |
| **Success** | `200` — an array of task objects (the requested page only). The array is empty when nothing matches or the page is past the last page |
| **Errors** | `400` VALIDATION_ERROR (bad query value) · `500` INTERNAL_ERROR |

**Parameters** (`TaskQuery`, built with its Builder. Every field is optional, and a `null` query means no filters)
| Name | Type | Required | Rules |
|---|---|---|---|
| `completed` | Boolean | no | `true` returns only completed tasks, `false` only open tasks; omitted returns both |
| `priority` | Priority | no | `LOW`, `MEDIUM` or `HIGH` |
| `search` | String | no | case-insensitive match on title or description |
| `sortBy` | String | no | `createdAt` or `dueDate`; default `createdAt`. Tasks without a `dueDate` sort last |
| `order` | String | no | `asc` or `desc`; default `asc` |
| `page` | int | no | 1 or greater; default 1. Page 1 is the first page |
| `pageSize` | int | no | 1 or greater; default 10 |

Processing order: filter, search, sort, then paginate.

**Example request**
```
completed = false   priority = HIGH   search = "rent"
sortBy = dueDate    order = asc       page = 1   pageSize = 10
```
**Example success response**
```json
{ "status": 200, "body": [ { "id": 1, "title": "Pay rent", "description": "Monthly rent", "completed": false, "dueDate": "2026-12-01", "priority": "HIGH", "parentId": null, "createdAt": 1788000000000 } ] }
```
**Example error response** (`page = 0`)
```json
{
  "status": 400,
  "body": { "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "page", "issue": "must be 1 or greater" }] } }
}
```

### updateTask
| | |
|---|---|
| **Java method** | `ApiResponse updateTask(int id, TaskRequest request)` |
| **Purpose** | Replace the editable fields of an existing task. `id`, `parentId` and `createdAt` never change |
| **Success** | `200` — the updated task |
| **Errors** | `400` VALIDATION_ERROR · `404` NOT_FOUND · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `id` | int | yes | id of an existing task |
| `title` | String | yes | not empty or blank |
| `description` | String | yes | not empty or blank |
| `completed` | String | yes | exactly `"true"` or `"false"` |
| `dueDate` | String | no | a real calendar date, `YYYY-MM-DD` |
| `priority` | String | no | `LOW`, `MEDIUM` or `HIGH`; defaults to `MEDIUM` |

Validation is the same as `createTask`. Changing `completed` from `false` to `true` emits a `completed` event. Any other change emits `updated`.

**Example request**
```
id = 1   title = "Pay rent"   description = "Monthly rent"   completed = "true"
dueDate = "2026-12-01"   priority = "HIGH"
```
**Example success response**
```json
{ "status": 200, "body": { "id": 1, "title": "Pay rent", "description": "Monthly rent", "completed": true, "dueDate": "2026-12-01", "priority": "HIGH", "parentId": null, "createdAt": 1788000000000 } }
```
**Example error response** (`dueDate = "2026-02-30"`)
```json
{
  "status": 400,
  "body": { "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "dueDate", "issue": "must be a real date in YYYY-MM-DD format" }] } }
}
```

### deleteTask
| | |
|---|---|
| **Java method** | `ApiResponse deleteTask(int id)` |
| **Purpose** | Delete a task. If it is a parent, all of its subtasks are deleted too |
| **Success** | `200` — confirmation object with the deleted id and the number of subtasks removed |
| **Errors** | `404` NOT_FOUND · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `id` | int | yes | id of an existing task |

**Example request**
```
id = 1
```
**Example success response**
```json
{ "status": 200, "body": { "deleted": 1, "subtasksDeleted": 3 } }
```
**Example error response** (`id = 99`)
```json
{
  "status": 404,
  "body": { "error": { "code": "NOT_FOUND", "message": "Task 99 not found", "details": [] } }
}
```

### createTaskFromText
| | |
|---|---|
| **Java method** | `ApiResponse createTaskFromText(String text, String idempotencyKey)` |
| **Purpose** | Turn plain English into one task using the configured `TaskParser` (LLM or rule-based) |
| **Success** | `201` — the created task |
| **Errors** | `400` VALIDATION_ERROR (`text` empty or blank) · `422` CONFLICT (key reused with a different text) · `502` BAD_GATEWAY (model call failed or timed out, or its output failed validation; nothing saved) · `503` SERVICE_UNAVAILABLE (`LLM_API_KEY` not set while the LLM parser is selected) · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `text` | String | yes | not empty or blank |
| `idempotencyKey` | String | no | same key + same text returns the original response; the parser is not called again |

The parser output (`title`, `description`, `dueDate`, optional recurrence) is converted to a `TaskRequest` and goes through the same validation as `createTask`. The model output is never trusted, so a validation failure on it returns `502`, not `400`. Only successful responses are stored under the key.

**Example request**
```
text = "Remind me to pay rent on the 1st of every month"   idempotencyKey = "text-001"
```
**Example success response**
```json
{ "status": 201, "body": { "id": 2, "title": "Pay rent", "description": "Pay rent on the 1st of every month", "completed": false, "dueDate": "2026-09-01", "priority": "MEDIUM", "parentId": null, "createdAt": 1788000000000 } }
```
**Example error response** (model returned an empty title)
```json
{
  "status": 502,
  "body": { "error": { "code": "BAD_GATEWAY", "message": "Model output failed validation", "details": [{ "field": "title", "issue": "must not be empty" }] } }
}
```

### createSubtask
| | |
|---|---|
| **Java method** | `ApiResponse createSubtask(int parentId, TaskRequest request)` |
| **Purpose** | Create a task linked to a parent through `parentId` |
| **Success** | `201` — the created subtask, with `parentId` set |
| **Errors** | `400` VALIDATION_ERROR (invalid input, or the parent is itself a subtask) · `404` NOT_FOUND (parent does not exist) · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `parentId` | int | yes | id of an existing task that is not itself a subtask |
| `title` | String | yes | not empty or blank |
| `description` | String | yes | not empty or blank |
| `completed` | String | yes | exactly `"true"` or `"false"` |
| `dueDate` | String | no | a real calendar date, `YYYY-MM-DD` |
| `priority` | String | no | `LOW`, `MEDIUM` or `HIGH`; defaults to `MEDIUM` |

**Example request**
```
parentId = 1   title = "Find bank details"   description = "Look up landlord account"   completed = "false"
```
**Example success response**
```json
{ "status": 201, "body": { "id": 3, "title": "Find bank details", "description": "Look up landlord account", "completed": false, "dueDate": null, "priority": "MEDIUM", "parentId": 1, "createdAt": 1788000000000 } }
```
**Example error response** (`parentId` is already a subtask)
```json
{
  "status": 400,
  "body": { "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "parentId", "issue": "a subtask cannot have subtasks" }] } }
}
```

### listSubtasks
| | |
|---|---|
| **Java method** | `ApiResponse listSubtasks(int parentId)` |
| **Purpose** | List the subtasks of one task |
| **Success** | `200` — an array of task objects; empty when the task has no subtasks |
| **Errors** | `404` NOT_FOUND (parent does not exist) · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `parentId` | int | yes | id of an existing task |

**Example request**
```
parentId = 1
```
**Example success response**
```json
{ "status": 200, "body": [ { "id": 3, "title": "Find bank details", "description": "Look up landlord account", "completed": false, "dueDate": null, "priority": "MEDIUM", "parentId": 1, "createdAt": 1788000000000 } ] }
```
**Example error response** (`parentId = 99`)
```json
{
  "status": 404,
  "body": { "error": { "code": "NOT_FOUND", "message": "Task 99 not found", "details": [] } }
}
```

### breakDownTask
| | |
|---|---|
| **Java method** | `ApiResponse breakDownTask(int id, String idempotencyKey)` |
| **Purpose** | Split a task into subtasks using the configured `BreakdownStrategy` (LLM or rule-based) |
| **Success** | `201` — an array of the created subtasks. All are saved, or none |
| **Errors** | `400` VALIDATION_ERROR (the task is itself a subtask) · `404` NOT_FOUND · `422` CONFLICT (key reused with a different task id) · `502` BAD_GATEWAY (model call failed, or any returned item is invalid; nothing saved) · `503` SERVICE_UNAVAILABLE (`LLM_API_KEY` not set while the LLM strategy is selected) · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `id` | int | yes | id of an existing task that is not itself a subtask |
| `idempotencyKey` | String | no | same key + same `id` returns the original response; the strategy is not called again and no extra subtasks are created |

Every item the strategy returns is validated before anything is saved.

**Example request**
```
id = 1   idempotencyKey = "break-001"
```
**Example success response**
```json
{ "status": 201, "body": [
  { "id": 4, "title": "Find bank details", "description": "Look up landlord account", "completed": false, "dueDate": null, "priority": "MEDIUM", "parentId": 1, "createdAt": 1788000000000 },
  { "id": 5, "title": "Transfer money", "description": "Send the payment", "completed": false, "dueDate": null, "priority": "MEDIUM", "parentId": 1, "createdAt": 1788000000000 }
] }
```
**Example error response** (task 3 is already a subtask)
```json
{
  "status": 400,
  "body": { "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "id", "issue": "a subtask cannot be broken down" }] } }
}
```

### listByPriority
| | |
|---|---|
| **Java method** | `ApiResponse listByPriority(String level)` |
| **Purpose** | List all tasks of one priority |
| **Success** | `200` — an array of task objects; empty when none match |
| **Errors** | `400` VALIDATION_ERROR (`level` is not `LOW`, `MEDIUM` or `HIGH`) · `500` INTERNAL_ERROR |

**Parameters**
| Name | Type | Required | Rules |
|---|---|---|---|
| `level` | String | yes | exactly `LOW`, `MEDIUM` or `HIGH` |

**Example request**
```
level = "HIGH"
```
**Example success response**
```json
{ "status": 200, "body": [ { "id": 1, "title": "Pay rent", "description": "Monthly rent", "completed": false, "dueDate": "2026-12-01", "priority": "HIGH", "parentId": null, "createdAt": 1788000000000 } ] }
```
**Example error response** (`level = "URGENT"`)
```json
{
  "status": 400,
  "body": { "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "level", "issue": "must be LOW, MEDIUM or HIGH" }] } }
}
```

---

## Errors

### Error shape

Every error, from every operation, looks exactly like this:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [{ "field": "title", "issue": "must not be empty" }]
  }
}
```

| Field | Type | Meaning |
|---|---|---|
| `error.code` | string | Stable, machine-readable identifier (table below) |
| `error.message` | string | Short human-readable summary |
| `error.details` | array | Zero or more `{ "field", "issue" }` entries; empty list when there is nothing field-specific |

### Error codes

| Status | `code` | When it happens |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Input broke a rule; also: breaking down a subtask or giving a subtask a subtask; invalid priority level in `listByPriority` |
| 404 | `NOT_FOUND` | The task id (or parent id) does not exist |
| 422 | `CONFLICT` | An idempotency key was reused with a different request |
| 502 | `BAD_GATEWAY` | The model call failed or timed out, or its output failed validation. Nothing was saved |
| 503 | `SERVICE_UNAVAILABLE` | `LLM_API_KEY` is not set |
| 500 | `INTERNAL_ERROR` | Any unexpected failure. No stack trace in the body |
