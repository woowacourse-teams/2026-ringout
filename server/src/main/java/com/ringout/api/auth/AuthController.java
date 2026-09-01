package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginRequest;
import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueRequest;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.reason.AuthErrorReason;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.status.AuthSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController implements AuthControllerApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    @PostMapping("/{provider}/login")
    public ResponseEntity<CustomResponse<LoginResponse>> login(
        @PathVariable("provider") String provider,
        @RequestBody LoginRequest request
    ) {
        log.atInfo()
            .addKeyValue("event", "social_login_requested")
            .addKeyValue("provider", provider)
            .log("소셜 로그인 요청");

        LoginResponse response = authService.login(
            parseSocialProvider(provider),
            request.socialAccessToken()
        );

        return ResponseEntity.status(AuthSuccessStatus.LOGIN_SUCCESS.getHttpStatus())
            .body(CustomResponse.onSuccess(AuthSuccessStatus.LOGIN_SUCCESS, response));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<CustomResponse<SignupResponse>> signup(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody TermsAgreeRequest request
    ) {
        log.atInfo()
            .addKeyValue("event", "signup_requested")
            .log("회원가입 요청");

        String signupToken = resolveBearerToken(authorization);
        SignupResponse response = authService.signup(signupToken, request);

        return ResponseEntity.status(AuthSuccessStatus.SIGNUP_SUCCESS.getHttpStatus())
            .body(CustomResponse.onSuccess(AuthSuccessStatus.SIGNUP_SUCCESS, response));
    }

    @Override
    @PostMapping("/reissue")
    public ResponseEntity<CustomResponse<ReissueResponse>> reissue(
        @RequestBody ReissueRequest request
    ) {
        log.atInfo()
            .addKeyValue("event", "token_reissue_requested")
            .log("토큰 재발급 요청");

        ReissueResponse response = authService.reissue(request.refreshToken());

        return ResponseEntity.status(AuthSuccessStatus.TOKEN_REISSUED.getHttpStatus())
            .body(CustomResponse.onSuccess(AuthSuccessStatus.TOKEN_REISSUED, response));
    }

    private String resolveBearerToken(String authorization) {
        return authorization.startsWith("Bearer ")
            ? authorization.substring(7)
            : authorization;
    }

    private SocialProvider parseSocialProvider(String provider) {
        try {
            return SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.atWarn()
                .addKeyValue("event", "social_login_failed")
                .addKeyValue("reason", AuthErrorReason.UNSUPPORTED_PROVIDER.getReason())
                .addKeyValue("provider", provider)
                .log("소셜 로그인 실패");
            throw exception;
        }
    }
}
