package com.sra.journal_tracking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 400 Bad Request
    USER_EXISTED(
            HttpStatus.BAD_REQUEST,
            "Email is already in use!"
    ),

    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Invalid request data!"
    ),

    INVALID_OLD_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "Incorrect old password!"
    ),

    INVALID_RESET_TOKEN(
            HttpStatus.BAD_REQUEST,
            "Invalid or expired reset token!"
    ),

    // 401 Unauthorized
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "Incorrect email or password!"
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "You need to login to perform this action!"
    ),

    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "Invalid or expired token!"
    ),

    // 403 Forbidden
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action!"
    ),

    BOOKMARK_LIMIT_EXCEEDED(
            HttpStatus.FORBIDDEN,
            "Bookmark limit exceeded!"
    ),

    FOLLOW_LIMIT_EXCEEDED(
            HttpStatus.FORBIDDEN,
            "Follow limit exceeded!"
    ),

    // 404 Not Found
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "User not found!"
    ),

    ROLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Role not found!"
    ),

    BOOKMARK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Bookmark not found!"
    ),

    FOLLOW_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Follow not found!"
    ),

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Resource not found!"
    ),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "HTTP method not supported for this endpoint!"
    ),

    // 409 Conflict
    DUPLICATE_ENTRY(
            HttpStatus.CONFLICT,
            "Data already exists in the system!"
    ),

    // 500 Internal Server Error
    TOKEN_HASH_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Token processing error!"
    ),

    EMAIL_SEND_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to send email. Please try again later!"
    ),

    UNCATEGORIZED_EXCEPTION(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unknown system error occurred."
    );

    private final HttpStatus statusCode;
    private final String message;

    ErrorCode(HttpStatus statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}