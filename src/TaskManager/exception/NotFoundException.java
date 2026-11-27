package TaskManager.exception;

/** INSTRUCTOR-PROVIDED SKELETON (Step 3). 404 / NOT_FOUND, e.g. new NotFoundException("Task 99 not found"). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }
}
