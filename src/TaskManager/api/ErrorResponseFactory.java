package TaskManager.api;

import TaskManager.exception.ApiException;
import TaskManager.exception.BadGatewayException;
import TaskManager.exception.FieldIssue;
import TaskManager.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ErrorResponseFactory {
    private ErrorResponseFactory() {}

    public static ApiResponse from(ApiException e) {
        List<Map<String, String>> details = new ArrayList<>();
        List<FieldIssue> issues = List.of();
        if (e instanceof ValidationException validation) issues = validation.getIssues();
        if (e instanceof BadGatewayException gateway) issues = gateway.getIssues();
        for (FieldIssue issue : issues) {
            details.add(Map.of("field", issue.getField(), "issue", issue.getIssue()));
        }
        return error(e.getStatus(), Objects.toString(e.getCode(), "INTERNAL_ERROR"),
                Objects.toString(e.getMessage(), "Request failed"), details);
    }

    public static ApiResponse fromUnexpected(Exception e) {
        // Keep internal exception messages out of public responses.
        return error(500, "INTERNAL_ERROR", "Internal server error", List.of());
    }

    private static ApiResponse error(int status, String code, String message, List<Map<String, String>> details) {
        return new ApiResponse(status, Map.of("error", Map.of(
                "code", code, "message", message, "details", details)));
    }
}
