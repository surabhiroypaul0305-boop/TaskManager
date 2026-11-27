package TaskManager.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * INSTRUCTOR-PROVIDED SKELETON (Step 3). 502 / BAD_GATEWAY.
 * The upstream model failed, timed out, or returned output that did not pass validation.
 *
 * The optional issues list lets you carry the validation problems of the model's
 * output ("title: must not be empty") into the error shape's "details".
 */
public class BadGatewayException extends ApiException {

    private final List<FieldIssue> issues;

    public BadGatewayException(String message) {
        this(message, new ArrayList<>());
    }

    public BadGatewayException(String message, List<FieldIssue> issues) {
        super(502, "BAD_GATEWAY", message);
        this.issues = new ArrayList<>(issues);
    }

    public List<FieldIssue> getIssues() {
        return new ArrayList<>(issues);
    }
}
