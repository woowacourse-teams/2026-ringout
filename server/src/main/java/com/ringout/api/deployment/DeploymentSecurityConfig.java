package com.ringout.api.deployment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class DeploymentSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain deploymentSecurityFilterChain(HttpSecurity http,
            @Value("${app.deployment.ingest-token:}") String token) throws Exception {
        http.securityMatcher("/api/v1/internal/deployments", "/api/v1/internal/deployments/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().access((a, context) -> {
                    String provided = context.getRequest().getHeader("X-Deployment-Token");
                    return new AuthorizationDecision(fromServerHost(context.getRequest().getRemoteAddr())
                            && !token.isBlank() && provided != null
                            && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                                    provided.getBytes(StandardCharsets.UTF_8)));
                }))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                        .accessDeniedHandler((req, res, ex) -> res.setStatus(403)));
        return http.build();
    }

    /**
     * 내부 API는 같은 EC2의 deploy.sh·기록 수집 스크립트만 호출한다. 컨테이너 밖 호스트 요청은 Docker 브리지(사설) 주소로 보인다.
     * nginx를 거친 외부 요청은 forward-headers 설정으로 실제 클라이언트(공인) 주소가 되므로 토큰이 있어도 거부된다.
     */
    static boolean fromServerHost(String remoteAddress) {
        try {
            var address = InetAddress.getByName(remoteAddress);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException | RuntimeException e) {
            return false;
        }
    }
}
