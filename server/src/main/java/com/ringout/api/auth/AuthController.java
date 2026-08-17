package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginRequest;
import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

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
            description = """
                    Google, Kakao, Naver에서 발급받은 소셜 액세스 토큰으로 로그인합니다.

                    ### 요청 방법
                    - provider: google, kakao, naver 중 하나
                    - Request Body: socialAccessToken에 소셜 서비스에서 발급받은 액세스 토큰을 넣어주세요.

                    ### 처음 로그인하는 회원
                    1. POST /api/v1/auth/{provider}/login을 호출합니다.
                    2. isNewUser가 true이면 응답으로 signupToken이 반환됩니다.
                    3. Authorization 헤더에 Bearer {signupToken}을 넣고, 약관 동의 정보와 함께 POST /api/v1/auth/signup을 호출합니다.
                    4. 회원가입 응답으로 받은 accessToken과 refreshToken을 저장합니다.

                    ### 기존 회원
                    POST /api/v1/auth/{provider}/login을 호출하면 isNewUser가 false이고,
                    accessToken과 refreshToken이 바로 반환됩니다.

                    ### 로그인 이후
                    인증이 필요한 API는 Authorization 헤더에 Bearer {accessToken}을 넣어 호출해주세요.
                    """
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
                    description = "소셜 로그인 제공자 (google, kakao, naver 중 하나)",
                    example = "kakao",
                    required = true
            )
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "소셜 제공자가 발급한 액세스 토큰을 socialAccessToken 필드에 넣어 보냅니다.",
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

    @Operation(
            summary = "회원가입",
            description = """
                    처음 소셜 로그인하는 회원의 약관 동의와 회원 생성을 완료합니다.

                    ### 요청 방법
                    - Authorization 헤더에 소셜 로그인 API에서 받은 {signupToken}을 넣어주세요.
                    - termsTypes에 필수 약관인 SERVICE와 PRIVACY를 모두 넣어주세요.
                    - agreedAt은 yyyy-MM-dd 형식의 동의 날짜입니다.

                    회원가입이 완료되면 accessToken과 refreshToken이 반환됩니다.
                    accessToken을 Authorization 헤더에 넣어주세요. (이 순간 부터 로그인 완료 상태입니다.)
                    """
    )
    @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "가입 토큰 또는 약관 동의가 올바르지 않음"),
            @ApiResponse(responseCode = "409", description = "이미 가입한 회원")
    })
    @PostMapping("/signup")
    public CustomResponse<SignupResponse> signup(
            @Parameter(hidden = true)
            @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "동의한 약관 종류와 동의 날짜",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "termsTypes": [
                                        "SERVICE", "PRIVACY"
                                      ],
                                      "agreedAt": "2024-03-01"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody TermsAgreeRequest request
    ) {
        String signupToken = resolveBearerToken(authorization);
        SignupResponse response = authService.signup(signupToken, request);

        return CustomResponse.onSuccess(response);
    }

    private String resolveBearerToken(String authorization) {
        return authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
    }
}
