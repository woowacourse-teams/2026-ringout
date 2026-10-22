package com.ringout.api.admin.security;

import com.ringout.api.admin.domain.AdminAccount;
import com.ringout.api.admin.domain.AdminStatus;
import com.ringout.api.admin.service.AdminAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AdminAccountService accounts;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        AdminAccount account;
        try {
            account = accounts.login((OidcUser) authentication.getPrincipal());
        } catch (OAuth2AuthenticationException exception) {
            clearSession(request);
            writeError(response, 401, "ADMIN_INVALID_GOOGLE_IDENTITY");
            return;
        }
        if (!account.isApproved()) {
            clearSession(request);
            writeError(response, 403, account.getStatus() == AdminStatus.PENDING
                    ? "ADMIN_APPROVAL_PENDING" : "ADMIN_ACCESS_REVOKED");
            return;
        }
        // Keep only the application's admin identity in the authenticated session.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(account.getId(), account.getEmail(), account.getName()), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        request.getSession().setMaxInactiveInterval(1800);
        response.sendRedirect(request.getContextPath() + "/api/v1/admin/auth/me");
    }

    public static void clearSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    public static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
