package com.ringout.api.common.response.code;

public interface BaseErrorCode {

  ErrorReasonResponse getReason();

  ErrorReasonResponse getReasonHttpStatus();
}
