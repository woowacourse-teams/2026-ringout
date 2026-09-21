package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.TestLoginRequest;
import com.ringout.api.auth.status.AuthSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.test-login",
    name = "enabled",
    havingValue = "true"
)
public class TestLoginController {

    private final TestLoginService testLoginService;

    @PostMapping("/test-login")
    public ResponseEntity<CustomResponse<LoginResponse>> login(
        @Parameter(hidden = true)
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody TestLoginRequest request
    ) {
        LoginResponse response = testLoginService.login(
            authorization,
            request.testUserId()
        );

        return ResponseEntity.status(AuthSuccessStatus.LOGIN_SUCCESS.getHttpStatus())
            .body(CustomResponse.onSuccess(AuthSuccessStatus.LOGIN_SUCCESS, response));
    }
}
