package com.ringout.api.config.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.member.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET = "test-secret-key-must-be-long-enough-for-hmac-sha-256!!";
  private static final Long USER_ID = 1L;
  private static final String PROVIDER_ID = "provider-id";

  @Test
  void 만료된_토큰이면_true를_반환한다() {
    // given
    Instant past = Instant.now().minusSeconds(10);
    Clock pastClock = Clock.fixed(past, ZoneOffset.UTC);
    JwtProvider jwtProvider = new JwtProvider(SECRET, 1L, 1L, 1L, pastClock);

    String expiredRefreshToken = jwtProvider.createRefreshToken(USER_ID, PROVIDER_ID, Role.USER);

    // when // then
    assertThat(jwtProvider.isExpiredToken(expiredRefreshToken)).isTrue();
    assertThat(jwtProvider.isValid(expiredRefreshToken)).isFalse();
  }

  @Test
  void 만료되지_않은_토큰이면_false를_반환한다() {
    // given
    JwtProvider jwtProvider = new JwtProvider(SECRET, 60_000L, 60_000L, 60_000L, Clock.systemUTC());

    String refreshToken = jwtProvider.createRefreshToken(USER_ID, PROVIDER_ID, Role.USER);

    // when // then
    assertThat(jwtProvider.isExpiredToken(refreshToken)).isFalse();
    assertThat(jwtProvider.isValid(refreshToken)).isTrue();
  }

  @Test
  void 서명이_다른_토큰이면_만료여부와_무관하게_false를_반환한다() {
    // given
    JwtProvider issuer = new JwtProvider(SECRET, 60_000L, 60_000L, 60_000L, Clock.systemUTC());
    JwtProvider verifier = new JwtProvider(
        "another-secret-key-must-be-long-enough-for-hmac-sha-256!!",
        60_000L, 60_000L, 60_000L, Clock.systemUTC()
    );

    String tokenSignedByIssuer = issuer.createRefreshToken(USER_ID, PROVIDER_ID, Role.USER);

    // when // then
    assertThat(verifier.isExpiredToken(tokenSignedByIssuer)).isFalse();
    assertThat(verifier.isValid(tokenSignedByIssuer)).isFalse();
  }
}
