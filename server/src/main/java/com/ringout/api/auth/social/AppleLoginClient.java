package com.ringout.api.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppleLoginClient implements SocialLoginClient {

    private static final String KEY_USE_SIGNATURE = "sig";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String KEY_TYPE_RSA = "RSA";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;

    public AppleLoginClient(
        @Value("${app.auth.apple.client-id}") String clientId
    ) {
        this.clientId = clientId;
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.APPLE;
    }

    @Override
    public SocialUserInfo authenticate(String token) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("애플 client id가 설정되어 있지 않습니다.");
        }

        AppleTokenHeader tokenHeader = parseTokenHeader(token);

        ApplePublicKey publicKey = findApplePublicKey(tokenHeader.kid(), tokenHeader.alg());

        Claims claims = parseClaims(token, publicKey);

        String providerId = claims.getSubject();
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("애플 사용자 식별자를 가져올 수 없습니다.");
        }

        return new SocialUserInfo(
            SocialProvider.APPLE,
            providerId,
            claims.get("email", String.class)
        );
    }

    private AppleTokenHeader parseTokenHeader(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("애플 ID 토큰은 비어 있을 수 없습니다.");
        }

        String[] tokenParts = token.split("\\.");
        if (tokenParts.length != 3) {
            throw new IllegalArgumentException("애플 ID 토큰 형식이 올바르지 않습니다.");
        }

        try {
            byte[] decodedHeader = Base64.getUrlDecoder().decode(tokenParts[0]);
            JsonNode header = objectMapper.readTree(decodedHeader);
            return new AppleTokenHeader(header.path("kid").asText(), header.path("alg").asText());
        } catch (Exception exception) {
            throw new IllegalArgumentException("애플 ID 토큰 헤더를 읽을 수 없습니다.", exception);
        }
    }

    private ApplePublicKey findApplePublicKey(String keyId, String algorithm) {
        ApplePublicKeysResponse response = restClient.get()
            .uri(APPLE_PUBLIC_KEYS_URL)
            .retrieve()
            .body(ApplePublicKeysResponse.class);

        if (response == null || response.keys() == null) {
            throw new IllegalArgumentException("애플 공개키를 가져올 수 없습니다.");
        }

        return Arrays.stream(response.keys())
            .filter(key -> KEY_TYPE_RSA.equals(key.kty()))
            .filter(key -> KEY_USE_SIGNATURE.equals(key.use()))
            .filter(key -> keyId.equals(key.kid()))
            .filter(key -> algorithm.equals(key.alg()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("애플 ID 토큰과 일치하는 공개키가 없습니다."));
    }

    private Claims parseClaims(String token, ApplePublicKey publicKey) {
        try {
            // parseClaimsJws 단계에서 RSA 서명과 exp 만료 시간이 검증됩니다.
            // requireIssuer/requireAudience는 이 토큰이 Apple에서, 우리 앱을 대상으로 발급됐는지 확인합니다.
            return Jwts.parserBuilder()
                .requireIssuer(APPLE_ISSUER)
                .requireAudience(clientId)
                .setSigningKey(toPublicKey(publicKey))
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("애플 ID 토큰이 유효하지 않습니다.", exception);
        }
    }

    // n, e를 이용해 RSA 공개키를 제작합니다.
    private PublicKey toPublicKey(ApplePublicKey publicKey) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(publicKey.n()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(publicKey.e()));
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);
            return KeyFactory.getInstance(KEY_TYPE_RSA).generatePublic(publicKeySpec);
        } catch (Exception exception) {
            throw new IllegalArgumentException("애플 공개키를 변환할 수 없습니다.", exception);
        }
    }

    private record AppleTokenHeader(String kid, String alg) {

    }

    private record ApplePublicKeysResponse(ApplePublicKey[] keys) {

    }

    private record ApplePublicKey(
        String kty,
        String kid,
        String use,
        String alg,
        String n,
        String e
    ) {

    }
}
