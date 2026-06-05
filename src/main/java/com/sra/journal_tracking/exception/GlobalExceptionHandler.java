package com.sra.journal_tracking.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.sra.journal_tracking.dto.response.ErrorResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===================== BUSINESS LOGIC EXCEPTIONS =====================

    // 1. Xử lý TẤT CẢ các lỗi logic nghiệp vụ do bạn tự ném (AppException)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.getStatusCode().value(),
                errorCode.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, errorCode.getStatusCode());
    }

    // ===================== AUTHENTICATION & AUTHORIZATION =====================

    // 2. Xử lý lỗi sai mật khẩu từ Spring Security (BadCredentials)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Email hoặc mật khẩu không chính xác!",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // 3. Xử lý lỗi xác thực chung (AuthenticationException)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // 4. Xử lý lỗi không có quyền truy cập (403 Forbidden)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Bạn không có quyền thực hiện hành động này!",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // 5. Xử lý lỗi không tìm thấy user trong Spring Security
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // ===================== VALIDATION ERRORS =====================

    // 6. Xử lý lỗi validation từ @Valid (MethodArgumentNotValidException)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Dữ liệu đầu vào không hợp lệ",
                fieldErrors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 7. Xử lý lỗi validation từ @Validated trên path variable / request param (ConstraintViolationException)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            fieldErrors.put(fieldName, errorMessage);
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Dữ liệu đầu vào không hợp lệ",
                fieldErrors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 8. Xử lý lỗi thiếu tham số bắt buộc (MissingServletRequestParameterException)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Thiếu tham số bắt buộc: " + ex.getParameterName(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 9. Xử lý lỗi sai kiểu dữ liệu tham số (MethodArgumentTypeMismatchException)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Tham số '" + ex.getName() + "' phải có kiểu dữ liệu là '"
                        + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "phù hợp") + "'",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 10. Xử lý lỗi request body không đọc được (HttpMessageNotReadableException - malformed JSON)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Request body không hợp lệ hoặc thiếu dữ liệu!",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 11. Xử lý lỗi sai HTTP method (HttpRequestMethodNotSupportedException)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Phương thức HTTP '" + ex.getMethod() + "' không được hỗ trợ cho endpoint này!",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // 12. Xử lý lỗi IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // ===================== DATABASE ERRORS =====================

    // 13. Xử lý lỗi ràng buộc dữ liệu (DataIntegrityViolationException - duplicate key, foreign key, ...)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = "Dữ liệu vi phạm ràng buộc hệ thống!";
        String detailMessage = ex.getMostSpecificCause().getMessage();

        if (detailMessage != null && detailMessage.toLowerCase().contains("duplicate")) {
            message = "Dữ liệu đã tồn tại trong hệ thống!";
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                message,
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    // ===================== LEGACY SUPPORT =====================

    // 14. Dành cho các lỗi cũ chưa kịp refactor sang AppException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // ===================== FALLBACK =====================

    // 15. Bắt các lỗi hệ thống chưa được xử lý (NullPointer, Database Error...)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        // In lỗi ra log server để backend dev biết đường fix
        ex.printStackTrace();

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Đã xảy ra lỗi hệ thống",
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}