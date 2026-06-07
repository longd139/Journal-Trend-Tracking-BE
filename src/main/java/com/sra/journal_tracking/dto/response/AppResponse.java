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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppResponse<T> {

    private int status;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory methods for convenience
    public static <T> AppResponse<T> success(String message, T data) {
        return AppResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> AppResponse<T> success(String message) {
        return AppResponse.<T>builder()
                .status(200)
                .message(message)
                .build();
    }

    public static <T> AppResponse<T> of(int status, String message, T data) {
        return AppResponse.<T>builder()
                .status(status)
                .message(message)
                .data(data)
                .build();
    }
}
