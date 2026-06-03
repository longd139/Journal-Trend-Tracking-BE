package com.sra.journal_tracking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Tự động ẩn field nào bị null trong JSON
public class ErrorResponse {
    private int status;
    private String message;
    private Object errors;

    // Tự động gán thời gian hiện tại khi object được tạo ra
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Giữ lại Constructor 3 tham số để tương thích với GlobalExceptionHandler của
    // bạn
    public ErrorResponse(int status, String message, Object errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }
}