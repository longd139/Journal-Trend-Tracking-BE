package com.sra.journal_tracking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ---- LỖI AUTHENTICATION (ĐĂNG NHẬP / ĐĂNG KÝ) ----
    USER_EXISTED(HttpStatus.BAD_REQUEST, "Email is already in use!"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Your account has not been verified. Please verify your email before continuing."),

    // ---- LỖI VERIFICATION TOKEN ----
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "The token is invalid or has already been used."),
    VERIFICATION_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "The token has expired. Please request a new one."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "Email has already been verified."),

    // ---- CÁC LỖI KHÁC THÊM SAU NÀY ----
    // INVALID_ROLE(HttpStatus.BAD_REQUEST, "Role không hợp lệ!"),

    // ---- LỖI BOOKMARK ----
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "You have already bookmarked this item!"),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "Bookmark not found!"),
    BOOKMARK_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "You have reached the bookmark limit. Upgrade to Researcher for unlimited bookmarks."),
    BOOKMARK_INVALID_TARGET(HttpStatus.BAD_REQUEST, "Exactly one of paperId or keywordId must be provided."),

    // ---- LỖI FOLLOW ----
    FOLLOW_ALREADY_EXISTS(HttpStatus.CONFLICT, "You are already following this target!"),
    FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Follow not found!"),
    FOLLOW_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "You have reached the follow limit. Upgrade to Researcher for unlimited follows."),
    FOLLOW_INVALID_TARGET(HttpStatus.BAD_REQUEST, "Exactly one of journalId, topicId, or keywordId must be provided."),

    // ---- LỖI TOPIC ----
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "Research topic not found!"),

    // ---- LỖI BOOKMARK COLLECTION ----
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Bookmark collection not found!"),
    COLLECTION_NAME_EXISTS(HttpStatus.CONFLICT, "You already have a collection with this name!"),
    COLLECTION_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "You have reached the collection limit. Upgrade to Researcher for unlimited collections."),

    // ---- LỖI CHUNG ----
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found!"),

    // ---- LỖI HỆ THỐNG (Fallback) ----
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected system error occurred.");

    private final HttpStatus statusCode;
    private final String message;

    ErrorCode(HttpStatus statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
