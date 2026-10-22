package com.ringout.api.admin.controller;

import com.ringout.api.admin.dto.response.AdminCsrfResponse;
import com.ringout.api.admin.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "관리자 (ADMIN)", description = "관리자용 API")
@SecurityScheme(name = "Admin Session", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE, paramName = "JSESSIONID")
public class AdminAuthController {
    private final boolean enabled;

    public AdminAuthController(@Value("${app.admin.google.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @GetMapping("/login")
    @Operation(summary = "관리자 구글 로그인 시작", security = {},
            description = "브라우저에서 이 URL로 이동하세요. Swagger Try it out은 외부 구글 로그인 이동을 지원하지 않습니다. "
                    + "최초 로그인은 PENDING으로 등록되어 403을 반환합니다. DB 승인 후 다시 로그인하세요.")
    public ResponseEntity<Void> login() {
        if (!enabled) return ResponseEntity.status(503).build();
        return ResponseEntity.status(302)
                .location(URI.create("/api/v1/admin/oauth2/authorization/google")).build();
    }

    @GetMapping("/me")
    @Operation(summary = "승인된 관리자 정보", security = @SecurityRequirement(name = "Admin Session"))
    public AdminPrincipal me(@AuthenticationPrincipal AdminPrincipal principal) { return principal; }

    @GetMapping("/csrf")
    @Operation(summary = "관리자 CSRF 토큰 조회", security = {},
            description = "로그인 후 다시 조회하세요. 변경 요청 및 로그아웃 시 headerName 헤더에 token을 전달합니다.")
    public AdminCsrfResponse csrf(CsrfToken csrfToken) {
        return new AdminCsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "관리자 로그아웃", security = @SecurityRequirement(name = "Admin Session"),
            description = "CSRF 헤더 필수. 보안 필터에서 세션을 무효화하고 204를 반환합니다.")
    public void logout() { /* handled by Spring Security */ }

}
