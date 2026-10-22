package com.ringout.api.admin.config;

import com.ringout.api.admin.security.AdminLoginSuccessHandler;
import com.ringout.api.admin.security.AdminPrincipal;
import com.ringout.api.admin.service.AdminAccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class AdminSecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "app.admin.google.enabled", havingValue = "true")
    ClientRegistrationRepository adminClientRegistrationRepository(
            @Value("${app.admin.google.client-id:}") String clientId,
            @Value("${app.admin.google.client-secret:}") String clientSecret,
            @Value("${app.admin.google.redirect-uri:}") String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Admin Google client ID, secret and redirect URI are required");
        }
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                .clientId(clientId).clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri).scope("openid", "email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub").clientName("Google Admin").build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations,
            AdminAccountService accounts, AdminLoginSuccessHandler successHandler) throws Exception {
        http.securityMatcher("/api/v1/admin", "/api/v1/admin/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/auth/login", "/api/v1/admin/auth/csrf",
                                "/api/v1/admin/oauth2/authorization/google",
                                "/api/v1/admin/login/oauth2/code/google").permitAll()
                        .anyRequest().access((authentication, context) -> {
                            var current = authentication.get();
                            return new AuthorizationDecision(current != null && current.isAuthenticated()
                                    && current.getPrincipal() instanceof AdminPrincipal principal
                                    && accounts.isApproved(principal));
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) ->
                                AdminLoginSuccessHandler.writeError(res, 401, "ADMIN_UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, ex) ->
                                AdminLoginSuccessHandler.writeError(res, 403, "ADMIN_ACCESS_DENIED")))
                .logout(logout -> logout.logoutUrl("/api/v1/admin/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true)
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        // CSRF protection stays enabled for the admin chain only.
        ClientRegistrationRepository clients = registrations.getIfAvailable();
        if (clients != null) {
            http.oauth2Login(oauth -> oauth.clientRegistrationRepository(clients)
                    .loginPage("/api/v1/admin/auth/login")
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/v1/admin/oauth2/authorization"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/admin/login/oauth2/code/*"))
                    .successHandler(successHandler)
                    .failureHandler((req, res, ex) -> {
                        AdminLoginSuccessHandler.clearSession(req);
                        AdminLoginSuccessHandler.writeError(res, 401, "ADMIN_GOOGLE_LOGIN_FAILED");
                    }));
        }
        return http.build();
    }
}
