package com.sra.journal_tracking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USER_EXISTED(
            HttpStatus.BAD_REQUEST,
            "Email is already in use!"
    ),

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy người dùng này!"
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "Email hoặc mật khẩu không chính xác!"
    ),

    BOOKMARK_LIMIT_EXCEEDED(
            HttpStatus.FORBIDDEN,
            "Bookmark limit exceeded!"
    ),

    FOLLOW_LIMIT_EXCEEDED(
            HttpStatus.FORBIDDEN,
            "Follow limit exceeded!"
    ),

    BOOKMARK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Bookmark not found!"
    ),

    FOLLOW_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Follow not found!"
    ),

    UNCATEGORIZED_EXCEPTION(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Đã xảy ra lỗi hệ thống không xác định."
    );

    private final HttpStatus statusCode;
    private final String message;

    ErrorCode(HttpStatus statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}