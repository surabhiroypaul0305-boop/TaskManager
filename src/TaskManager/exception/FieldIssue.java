package TaskManager.exception;

/**
 * INSTRUCTOR-PROVIDED SKELETON (Step 3).
 * One problem with one field, e.g. new FieldIssue("title", "must not be empty").
 * Becomes one entry of "details" in the error shape.
 */
public class FieldIssue {

    private final String field;
    private final String issue;

    public FieldIssue(String field, String issue) {
        this.field = field;
        this.issue = issue;
    }

    public String getField() {
        return field;
    }

    public String getIssue() {
        return issue;
    }
}
