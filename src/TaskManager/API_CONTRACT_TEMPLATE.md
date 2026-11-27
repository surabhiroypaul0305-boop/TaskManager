# API_CONTRACT.md — Template (Step 10)

> Hand out this file. Students copy it to the root of their repo as `API_CONTRACT.md`, keep the
> **createTask** entry as the model, and write one entry for each remaining operation.
> Written by hand — not generated. If the service is later exposed over HTTP, this becomes the OpenAPI file.

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

### getTask — TODO (student)
### listTasks — TODO (student)
### updateTask — TODO (student)
### deleteTask — TODO (student)
### createTaskFromText — TODO (student)
### createSubtask — TODO (student)
### listSubtasks — TODO (student)
### breakDownTask — TODO (student)
### listByPriority — TODO (student)

*(For each: Java method, purpose, success status, error statuses, parameters table, one example request, one example success response, one example error response.)*

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
