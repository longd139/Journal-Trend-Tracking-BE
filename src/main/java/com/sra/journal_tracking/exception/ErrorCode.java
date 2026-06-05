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
            "Dữ liệu yêu cầu không hợp lệ!"
    ),

    INVALID_OLD_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "Mật khẩu cũ không chính xác!"
    ),

    // 401 Unauthorized
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "Email hoặc mật khẩu không chính xác!"
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Bạn cần đăng nhập để thực hiện hành động này!"
    ),

    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "Token không hợp lệ hoặc đã hết hạn!"
    ),

    // 403 Forbidden
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "Bạn không có quyền thực hiện hành động này!"
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
            "Không tìm thấy người dùng này!"
    ),

    ROLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy vai trò này!"
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
            "Không tìm thấy tài nguyên yêu cầu!"
    ),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Phương thức HTTP không được hỗ trợ cho endpoint này!"
    ),

    // 409 Conflict
    DUPLICATE_ENTRY(
            HttpStatus.CONFLICT,
            "Dữ liệu đã tồn tại trong hệ thống!"
    ),

    // 500 Internal Server Error
    TOKEN_HASH_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Lỗi xử lý token bảo mật!"
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