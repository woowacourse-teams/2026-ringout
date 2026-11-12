package com.ringout.api.config.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jwt.revocation-enabled", havingValue = "true")
public class RedisTokenBlacklist {

  private final StringRedisTemplate redis;
  private final Clock clock;

  public void revoke(String token, Instant expiresAt) {
    Duration ttl = Duration.between(clock.instant(), expiresAt);
    if (ttl.toMillis() > 0) {
      redis.opsForValue().set(key(token), "1", ttl);
    }
  }

  public boolean contains(String token) {
    return Boolean.TRUE.equals(redis.hasKey(key(token)));
  }

  private String key(String token) {
    // Use the signed data so an alternate encoding of the signature cannot bypass revocation.
    int headerEnd = token.indexOf('.');
    int payloadEnd = token.lastIndexOf('.');
    String signingInput = token.substring(0, headerEnd).trim() + "."
        + token.substring(headerEnd + 1, payloadEnd).trim();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(signingInput.getBytes(StandardCharsets.UTF_8));
      return "ringout:jwt:blacklist:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
