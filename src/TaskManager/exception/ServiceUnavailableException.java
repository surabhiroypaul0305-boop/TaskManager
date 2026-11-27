package TaskManager.exception;

/** INSTRUCTOR-PROVIDED SKELETON (Step 3). 503 / SERVICE_UNAVAILABLE, e.g. LLM_API_KEY is not set. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(503, "SERVICE_UNAVAILABLE", message);
    }
}
