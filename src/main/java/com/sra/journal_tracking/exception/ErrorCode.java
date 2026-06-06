package com.sra.journal_tracking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ---- LỖI AUTHENTICATION (ĐĂNG NHẬP / ĐĂNG KÝ) ----
    USER_EXISTED(HttpStatus.BAD_REQUEST, "Email is already in use!"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng này!"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác!"),

    // ---- CÁC LỖI KHÁC THÊM SAU NÀY ----
    // INVALID_ROLE(HttpStatus.BAD_REQUEST, "Role không hợp lệ!"),

    // ---- LỖI HỆ THỐNG (Fallback) ----
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống không xác định.");

    private final HttpStatus statusCode;
    private final String message;

    ErrorCode(HttpStatus statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}