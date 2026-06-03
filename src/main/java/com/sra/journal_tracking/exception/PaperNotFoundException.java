package com.sra.journal_tracking.exception;

public class PaperNotFoundException extends RuntimeException {
    public PaperNotFoundException(String message) {
        super(message);
    }
}