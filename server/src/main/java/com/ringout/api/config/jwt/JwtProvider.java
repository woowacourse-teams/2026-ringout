package com.ringout.api.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.ringout.api.member.domain.Role;
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

  private final SecretKey secretKey;
  private final long accessTokenExpirationMillis;
  private final Clock clock;

  public JwtProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-expiration-millis}") long accessTokenExpirationMillis,
      Clock clock
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMillis = accessTokenExpirationMillis;
    this.clock = clock;
  }

  public String createAccessToken(Long userId, String providerId, Role role) {
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
    Instant expiresAt = issuedAt.plusMillis(accessTokenExpirationMillis);

    return Jwts.builder()
        .setSubject(userId.toString())
        .claim(USER_ID_CLAIM, userId)
        .claim(PROVIDER_ID_CLAIM, providerId)
        .claim(ROLE_CLAIM, role.name())
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
