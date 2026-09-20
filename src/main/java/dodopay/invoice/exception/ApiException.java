package dodopay.invoice.exception;

public class ApiException extends RuntimeException {
    private final int statusCode;
    private final String errorType;

    public ApiException(int statusCode, String errorType, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "bad_request", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "unauthorized", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "not_found", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "conflict", message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, "unprocessable_entity", message);
    }

    public int getStatusCode() { return statusCode; }
    public String getErrorType() { return errorType; }
}
