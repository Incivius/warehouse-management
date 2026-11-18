
package com.warehouse.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        int status,
        LocalDateTime timestamp
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getStatus().value(),
                LocalDateTime.now()
        );
    }
}
