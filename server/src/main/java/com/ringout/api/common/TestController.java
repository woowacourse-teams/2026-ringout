package com.ringout.api.common;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.status.SuccessStatus;
import com.ringout.api.config.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController implements TestControllerApi {

    public TestController() {
    }

    @Override
    @GetMapping("/test")
    public ResponseEntity<CustomResponse<String>> test() {
        String response = "Hello from Spring Boot 👋";
        return ResponseEntity.status(SuccessStatus._OK.getHttpStatus())
            .body(CustomResponse.onSuccess(SuccessStatus._OK, response));
    }

    @Override
    @GetMapping("/test-auth")
    public ResponseEntity<CustomResponse<String>> testAuth(
        @AuthenticationPrincipal CustomUserDetails user
    ) {
        String response = "Hello from Spring Boot 👋";
        return ResponseEntity.status(SuccessStatus._OK.getHttpStatus())
            .body(CustomResponse.onSuccess(SuccessStatus._OK, response));
    }
}
