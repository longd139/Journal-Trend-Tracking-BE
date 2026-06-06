package com.sra.journal_tracking.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        // Kế thừa message của RuntimeException để sau này dễ debug trong console
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}