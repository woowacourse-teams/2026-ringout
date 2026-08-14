package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginRequest;
import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.status.SuccessStatus;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "소셜 로그인 API")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "소셜 로그인",
            description = "카카오 또는 구글 액세스 토큰을 검증하고 소셜 사용자 정보를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 소셜 제공자 또는 잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "소셜 제공자 통신 실패")
    })
    @PostMapping("/{provider}/login")
    public CustomResponse<LoginResponse> login(
            @Parameter(
                    name = "provider",
                    in = ParameterIn.PATH,
                    description = "소셜 로그인 제공자",
                    example = "kakao",
                    required = true
            )
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "소셜 제공자가 발급한 액세스 토큰",
                    required = true
            )
            @RequestBody LoginRequest request
    ) {
        return CustomResponse.onSuccess(
                authService.login(
                        SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT)),
                        request.socialAccessToken()
                )
        );
    }

    @Operation(summary = "회원가입", description = "가입 토큰을 검증하고 약관 동의와 회원 생성을 완료합니다.")
    @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "가입 토큰 또는 약관 동의가 올바르지 않음"),
            @ApiResponse(responseCode = "409", description = "이미 가입한 회원")
    })
    @PostMapping("/signup")
    public ResponseEntity<CustomResponse<SignupResponse>> signup(
            @Parameter(hidden = true)
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody TermsAgreeRequest request
    ) {
        String signupToken = resolveBearerToken(authorization);
        SignupResponse response = authService.signup(signupToken, request);

        return ResponseEntity.status(SuccessStatus._CREATED.getHttpStatus())
                .body(CustomResponse.of(SuccessStatus._CREATED, response));
    }

    private String resolveBearerToken(String authorization) {
        return authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
    }
}
