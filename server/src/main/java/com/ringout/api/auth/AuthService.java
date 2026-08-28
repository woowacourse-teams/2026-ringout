package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import com.ringout.api.auth.status.AuthErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.service.UserService;
import com.ringout.api.user.status.UserErrorStatus;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.service.TermsService;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return new SignupResponse(accessToken, refreshToken);
    }

    private void validateSignupToken(String signupToken) {
        if (!jwtProvider.isValid(signupToken) || !jwtProvider.isSignupToken(signupToken)) {
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(String refreshToken) {
        validateRefreshToken(refreshToken);

        Long userId = jwtProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        String reissuedAccessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getSocialProviderId(),
            user.getRole()
        );

        return ReissueResponse.of(reissuedAccessToken);
    }

    private void validateRefreshToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public LoginResponse login(SocialProvider provider, String socialAccessToken) {
        if (socialAccessToken == null || socialAccessToken.isBlank()) {
            throw new IllegalArgumentException("소셜 액세스 토큰은 비어 있을 수 없습니다.");
        }

        SocialLoginClient loginClient = loginClients.get(provider);

        if (loginClient == null) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        SocialUserInfo socialUserInfo = loginClient.authenticate(socialAccessToken);
        LocalDateTime loginAt = LocalDateTime.now();

        return userRepository
            .findBySocialProviderAndSocialProviderId(
                socialUserInfo.provider(),
                socialUserInfo.providerId()
            )
            .map(user -> loginExistingUser(user, socialUserInfo, loginAt))
            .orElseGet(() -> LoginResponse.signupRequired(
                jwtProvider.createSignupToken(
                    socialUserInfo.provider(),
                    socialUserInfo.providerId(),
                    socialUserInfo.email()
                )
            ));
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
        return LoginResponse.loggedIn(accessToken, refreshToken);
    }
}
