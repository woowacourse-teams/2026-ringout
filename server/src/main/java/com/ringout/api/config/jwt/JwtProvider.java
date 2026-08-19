package com.ringout.api.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.ringout.api.member.domain.Role;
import com.ringout.api.auth.social.SocialProvider;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private static final String USER_ID_CLAIM = "userId";
  private static final String PROVIDER_ID_CLAIM = "providerId";
  private static final String ROLE_CLAIM = "role";
  private static final String TOKEN_TYPE_CLAIM = "tokenType";
  private static final String SOCIAL_PROVIDER_CLAIM = "socialProvider";
  private static final String EMAIL_CLAIM = "email";

  private final SecretKey secretKey;
  private final long accessTokenExpirationMillis;
  private final long refreshTokenExpirationMillis;
  private final long signupTokenExpirationMillis;
  private final Clock clock;

  public JwtProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-expiration-millis}") long accessTokenExpirationMillis,
      @Value("${app.jwt.refresh-token-expiration-millis}") long refreshTokenExpirationMillis,
      @Value("${app.jwt.signup-token-expiration-millis}") long signupTokenExpirationMillis,
      Clock clock
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMillis = accessTokenExpirationMillis;
    this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    this.signupTokenExpirationMillis = signupTokenExpirationMillis;
    this.clock = clock;
  }

  public String createAccessToken(Long userId, String providerId, Role role) {
    return createUserToken(
        userId,
        providerId,
        role,
        TokenType.ACCESS,
        accessTokenExpirationMillis
    );
  }

  public String createRefreshToken(Long userId, String providerId, Role role) {
    return createUserToken(
        userId,
        providerId,
        role,
        TokenType.REFRESH,
        refreshTokenExpirationMillis
    );
  }

  private String createUserToken(
      Long userId,
      String providerId,
      Role role,
      TokenType tokenType,
      long expirationMillis
  ) {
    if (userId == null) {
      throw new IllegalArgumentException("회원 ID는 비어 있을 수 없습니다.");
    }
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("소셜 사용자 식별자는 비어 있을 수 없습니다.");
    }
    if (role == null) {
      throw new IllegalArgumentException("회원 역할은 비어 있을 수 없습니다.");
    }

    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plusMillis(expirationMillis);

    return Jwts.builder()
        .setSubject(userId.toString())
        .claim(USER_ID_CLAIM, userId)
        .claim(PROVIDER_ID_CLAIM, providerId)
        .claim(ROLE_CLAIM, role.name())
        .claim(TOKEN_TYPE_CLAIM, tokenType.name())
        .setIssuedAt(Date.from(issuedAt))
        .setExpiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public String createSignupToken(
      SocialProvider socialProvider,
      String providerId,
      String email
  ) {
    if (socialProvider == null) {
      throw new IllegalArgumentException("소셜 로그인 제공자는 비어 있을 수 없습니다.");
    }
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("소셜 사용자 식별자는 비어 있을 수 없습니다.");
    }

    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plusMillis(signupTokenExpirationMillis);

    return Jwts.builder()
        .claim(SOCIAL_PROVIDER_CLAIM, socialProvider.name())
        .claim(PROVIDER_ID_CLAIM, providerId)
        .claim(EMAIL_CLAIM, email)
        .claim(ROLE_CLAIM, Role.USER.name())
        .claim(TOKEN_TYPE_CLAIM, TokenType.SIGNUP.name())
        .setIssuedAt(Date.from(issuedAt))
        .setExpiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public boolean isValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public boolean isExpiredToken(String token) {
    try {
      parseClaims(token);
      return false;
    } catch (ExpiredJwtException exception) {
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public Long getUserId(String token) {
    Number userId = parseClaims(token).get(USER_ID_CLAIM, Number.class);
    if (userId == null) {
      throw new IllegalArgumentException("JWT 회원 ID가 올바르지 않습니다.");
    }
    return userId.longValue();
  }

  public String getProviderId(String token) {
    return parseClaims(token).get(PROVIDER_ID_CLAIM, String.class);
  }

  public Role getRole(String token) {
    String role = parseClaims(token).get(ROLE_CLAIM, String.class);
    return Role.valueOf(role);
  }

  public SocialProvider getSocialProvider(String token) {
    String provider = parseClaims(token).get(SOCIAL_PROVIDER_CLAIM, String.class);
    return SocialProvider.valueOf(provider);
  }

  public String getEmail(String token) {
    return parseClaims(token).get(EMAIL_CLAIM, String.class);
  }

  public boolean isAccessToken(String token) {
    try {
      return getTokenType(token) == TokenType.ACCESS;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public boolean isSignupToken(String token) {
    try {
      return getTokenType(token) == TokenType.SIGNUP;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      return getTokenType(token) == TokenType.REFRESH;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private TokenType getTokenType(String token) {
    String tokenType = parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    return TokenType.valueOf(tokenType);
  }

  private Claims parseClaims(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("JWT는 비어 있을 수 없습니다.");
    }

    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}
