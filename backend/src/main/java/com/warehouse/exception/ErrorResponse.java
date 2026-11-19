
package com.warehouse.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        int status,
        LocalDateTime timestamp
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return from(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse from(ErrorCode errorCode, String message) {
        return new ErrorResponse(
                errorCode.getCode(),
                message,
                errorCode.getStatus().value(),
                LocalDateTime.now()
        );
    }
}
