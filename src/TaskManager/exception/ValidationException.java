package TaskManager.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * INSTRUCTOR-PROVIDED SKELETON (Step 3). 400 / VALIDATION_ERROR.
 *
 * Collects ALL field issues so one request can report several problems at once:
 *   List<FieldIssue> issues = new ArrayList<>();
 *   if (title is blank)   issues.add(new FieldIssue("title", "must not be empty"));
 *   if (bad priority)     issues.add(new FieldIssue("priority", "must be LOW, MEDIUM or HIGH"));
 *   if (!issues.isEmpty()) throw new ValidationException(issues);
 */
public class ValidationException extends ApiException {

    private final List<FieldIssue> issues;

    public ValidationException(List<FieldIssue> issues) {
        super(400, "VALIDATION_ERROR", "Validation failed");
        this.issues = new ArrayList<>(issues);
    }

    /** Convenience for a single problem. */
    public ValidationException(String field, String issue) {
        this(List.of(new FieldIssue(field, issue)));
    }

    public List<FieldIssue> getIssues() {
        return new ArrayList<>(issues);
    }
}
