package com.sra.journal_tracking.exception;

public class UsageLimitExceededException extends RuntimeException {
    public UsageLimitExceededException(String message) {
        super(message);
    }
}