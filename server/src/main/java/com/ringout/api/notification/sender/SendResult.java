package com.ringout.api.notification.sender;

public enum SendResult {
    SENT,
    // 일시적 오류. 다시 보내면 성공할 수 있다.
    RETRYABLE,
    // 다시 보내도 성공할 수 없다.
    PERMANENT
}
