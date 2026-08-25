package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginRequest;
import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueRequest;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.status.AuthSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        LoginResponse response = authService.login(
            SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT)),
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
        ReissueResponse response = authService.reissue(request.refreshToken());

        return ResponseEntity.status(AuthSuccessStatus.TOKEN_REISSUED.getHttpStatus())
            .body(CustomResponse.onSuccess(AuthSuccessStatus.TOKEN_REISSUED, response));
    }

    private String resolveBearerToken(String authorization) {
        return authorization.startsWith("Bearer ")
            ? authorization.substring(7)
            : authorization;
    }
}
