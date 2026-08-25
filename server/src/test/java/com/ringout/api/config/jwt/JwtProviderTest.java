package com.ringout.api.config.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.user.domain.Role;
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

  @Test
  void 액세스_토큰을_발급하고_회원_ID를_추출한다() {
    JwtProvider jwtProvider = createProvider(3_600_000L);

    String token = jwtProvider.createAccessToken(USER_ID, PROVIDER_ID, Role.USER);

    assertThat(jwtProvider.isValid(token)).isTrue();
    assertThat(jwtProvider.getUserId(token)).isEqualTo(USER_ID);
    assertThat(jwtProvider.getProviderId(token)).isEqualTo(PROVIDER_ID);
    assertThat(jwtProvider.getRole(token)).isEqualTo(Role.USER);
  }

  @Test
  void 서명이_변조된_토큰은_유효하지_않다() {
    JwtProvider jwtProvider = createProvider(3_600_000L);
    String token = jwtProvider.createAccessToken(USER_ID, PROVIDER_ID, Role.USER);
    String tamperedToken = token.substring(0, token.length() - 1) + "x";

    assertThat(jwtProvider.isValid(tamperedToken)).isFalse();
  }

  @Test
  void 만료된_액세스_토큰은_유효하지_않다() {
    JwtProvider jwtProvider = createProvider(-1L);

    String token = jwtProvider.createAccessToken(USER_ID, PROVIDER_ID, Role.USER);

    assertThat(jwtProvider.isValid(token)).isFalse();
  }

  private JwtProvider createProvider(long expirationMillis) {
    Clock clock = Clock.systemUTC();
    return new JwtProvider(SECRET, expirationMillis, 1_209_600_000L, 600_000L, clock);
  }
}
