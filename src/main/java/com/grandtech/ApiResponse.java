package com.grandtech;

/**
 * Standard envelope returned by every API endpoint.
 *
 * @param <T> the type of the payload field
 */
public class ApiResponse<T> {

    /** HTTP status code (200 for success, 4xx/5xx for errors). */
    private final int status;

    /** Short outcome description, e.g. "success" or an error message. */
    private final String message;

    /** Response data — may be a JSON object, array, primitive, or null. */
    private final T payload;

    /**
     * Constructs a new ApiResponse.
     *
     * @param status  the HTTP status code
     * @param message a short outcome description
     * @param payload the response data
     */
    public ApiResponse(final int status, final String message, final T payload) {
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the outcome message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the response payload.
     *
     * @return the payload
     */
    public T getPayload() {
        return payload;
    }
}
