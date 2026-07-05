package com.sra.journal_tracking.exception;

/**
 * Thrown when a client exceeds their rate limit.
 * Handled by GlobalExceptionHandler → HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** How many seconds the client should wait before retrying. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
