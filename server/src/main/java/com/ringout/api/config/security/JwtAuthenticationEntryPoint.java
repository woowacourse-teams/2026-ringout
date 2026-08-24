package com.ringout.api.config.security;

import tools.jackson.databind.ObjectMapper;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.config.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;
  private final ObjectMapper objectMapper;

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {
    String token = resolveToken(request);
    ErrorStatus errorStatus = (token != null && jwtProvider.isExpiredToken(token))
        ? ErrorStatus.ACCESS_TOKEN_EXPIRED : ErrorStatus.UNAUTHORIZED;

    response.setStatus(errorStatus.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    CustomResponse<Void> body = CustomResponse.onFailure(errorStatus.getCode(),
        errorStatus.getMessage(), null);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  private String resolveToken(HttpServletRequest request) {
    String authorization = request.getHeader(AUTHORIZATION_HEADER);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }

    String token = authorization.substring(BEARER_PREFIX.length());
    return token.isBlank() ? null : token;
  }
}
