package com.ringout.api.common.response.code;

import org.springframework.http.HttpStatus;

public record ReasonResponse(
    HttpStatus httpStatus,
    boolean isSuccess,
    String code,
    String message
) {
}
