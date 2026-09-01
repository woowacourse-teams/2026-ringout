package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.reason.AuthErrorReason;
import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import com.ringout.api.auth.status.AuthErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.service.TermsService;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.service.UserService;
import com.ringout.api.user.status.UserErrorStatus;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    private final Map<SocialProvider, SocialLoginClient> loginClients;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final UserService userService;
    private final TermsService termsService;

    public AuthService(
        List<SocialLoginClient> loginClients,
        UserRepository userRepository,
        JwtProvider jwtProvider,
        UserService userService,
        TermsService termsService
    ) {
        this.loginClients = new EnumMap<>(SocialProvider.class);
        loginClients.forEach(client ->
            this.loginClients.put(client.supports(), client)
        );
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.userService = userService;
        this.termsService = termsService;
    }

    @Transactional
    public SignupResponse signup(String signupToken, TermsAgreeRequest request) {
        validateSignupToken(signupToken);

        SocialProvider socialProvider = jwtProvider.getSocialProvider(signupToken);
        String providerId = jwtProvider.getProviderId(signupToken);
        String email = jwtProvider.getEmail(signupToken);

        User user = userService.register(
            socialProvider,
            providerId,
            email,
            LocalDateTime.now()
        );
        termsService.termsAgree(user.getId(), request);

        String accessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );
        String refreshToken = jwtProvider.createRefreshToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );
        log.atInfo()
            .addKeyValue("event", "signup_succeeded")
            .addKeyValue("userId", user.getId())
            .addKeyValue("provider", socialProvider)
            .log("회원가입 성공");

        return new SignupResponse(accessToken, refreshToken);
    }

    private void validateSignupToken(String signupToken) {
        if (!jwtProvider.isValid(signupToken) || !jwtProvider.isSignupToken(signupToken)) {
            log.atWarn()
                .addKeyValue("event", "signup_failed")
                .addKeyValue("reason", AuthErrorReason.INVALID_SIGNUP_TOKEN.getReason())
                .log("회원가입 실패");
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(String refreshToken) {
        validateRefreshToken(refreshToken);

        Long userId = jwtProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.atWarn()
                    .addKeyValue("event", "token_reissue_failed")
                    .addKeyValue("reason", AuthErrorReason.REFRESH_TOKEN_USER_NOT_FOUND.getReason())
                    .addKeyValue("userId", userId)
                    .log("토큰 재발급 실패");
                return new GeneralException(UserErrorStatus.USER_NOT_FOUND);
            });

        String reissuedAccessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );
        log.atInfo()
            .addKeyValue("event", "token_reissue_succeeded")
            .addKeyValue("userId", user.getId())
            .addKeyValue("provider", user.getSocialProvider())
            .log("토큰 재발급 성공");

        return ReissueResponse.of(reissuedAccessToken);
    }

    private void validateRefreshToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            log.atWarn()
                .addKeyValue("event", "token_reissue_failed")
                .addKeyValue("reason", AuthErrorReason.INVALID_REFRESH_TOKEN.getReason())
                .log("토큰 재발급 실패");
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public LoginResponse login(SocialProvider provider, String socialAccessToken) {
        if (socialAccessToken == null || socialAccessToken.isBlank()) {
            log.atWarn()
                .addKeyValue("event", "social_login_failed")
                .addKeyValue("reason", AuthErrorReason.EMPTY_SOCIAL_ACCESS_TOKEN.getReason())
                .addKeyValue("provider", provider)
                .log("소셜 로그인 실패");
            throw new IllegalArgumentException("소셜 액세스 토큰은 비어 있을 수 없습니다.");
        }

        SocialLoginClient loginClient = loginClients.get(provider);

        if (loginClient == null) {
            log.atWarn()
                .addKeyValue("event", "social_login_failed")
                .addKeyValue("reason", AuthErrorReason.SOCIAL_LOGIN_CLIENT_NOT_FOUND.getReason())
                .addKeyValue("provider", provider)
                .log("소셜 로그인 실패");
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        SocialUserInfo socialUserInfo = authenticateSocialUser(provider, socialAccessToken, loginClient);
        LocalDateTime loginAt = LocalDateTime.now();

        return userRepository
            .findBySocialProviderAndSocialProviderId(
                socialUserInfo.provider(),
                socialUserInfo.providerId()
            )
            .map(user -> loginExistingUser(user, socialUserInfo, loginAt))
            .orElseGet(() -> requireSignup(socialUserInfo));
    }

    private SocialUserInfo authenticateSocialUser(
        SocialProvider provider,
        String socialAccessToken,
        SocialLoginClient loginClient
    ) {
        try {
            SocialUserInfo socialUserInfo = loginClient.authenticate(socialAccessToken);
            log.atInfo()
                .addKeyValue("event", "social_authentication_succeeded")
                .addKeyValue("provider", socialUserInfo.provider())
                .log("소셜 인증 성공");
            return socialUserInfo;
        } catch (RuntimeException exception) {
            log.atWarn()
                .addKeyValue("event", "social_authentication_failed")
                .addKeyValue("reason", AuthErrorReason.SOCIAL_AUTHENTICATION_FAILED.getReason())
                .addKeyValue("provider", provider)
                .addKeyValue("exception", exception.getClass().getName())
                .log("소셜 인증 실패");
            throw exception;
        }
    }

    private LoginResponse loginExistingUser(
        User user,
        SocialUserInfo socialUserInfo,
        LocalDateTime loginAt
    ) {
        user.login(loginAt, socialUserInfo.email());
        String accessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );
        String refreshToken = jwtProvider.createRefreshToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );
        log.atInfo()
            .addKeyValue("event", "social_login_succeeded")
            .addKeyValue("userId", user.getId())
            .addKeyValue("provider", socialUserInfo.provider())
            .log("소셜 로그인 성공");

        return LoginResponse.loggedIn(accessToken, refreshToken);
    }

    private LoginResponse requireSignup(SocialUserInfo socialUserInfo) {
        String signupToken = jwtProvider.createSignupToken(
            socialUserInfo.provider(),
            socialUserInfo.providerId(),
            socialUserInfo.email()
        );
        log.atInfo()
            .addKeyValue("event", "signup_required")
            .addKeyValue("provider", socialUserInfo.provider())
            .log("신규 회원 가입 필요");
        log.atInfo()
            .addKeyValue("event", "signup_token_issued")
            .addKeyValue("provider", socialUserInfo.provider())
            .log("회원가입 토큰 발급 성공");

        return LoginResponse.signupRequired(signupToken);
    }
}
