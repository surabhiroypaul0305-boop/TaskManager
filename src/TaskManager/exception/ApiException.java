package TaskManager.exception;

/**
 * INSTRUCTOR-PROVIDED SKELETON (Step 3).
 *
 * Base of the hierarchy. Carries the HTTP-style status and a machine-readable
 * error code; the human-readable text goes in getMessage().
 *
 *   ApiException (RuntimeException)
 *     |- ValidationException          400  VALIDATION_ERROR
 *     |- NotFoundException            404  NOT_FOUND
 *     |- ConflictException            422  CONFLICT
 *     |- BadGatewayException          502  BAD_GATEWAY
 *     |- ServiceUnavailableException  503  SERVICE_UNAVAILABLE
 *
 * Unchecked on purpose: services throw it from anywhere, and only TaskApi catches it.
 *
 * When you copy the exception classes into your own project, change the package line
 * to: package exception;   (and fix the imports)
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
