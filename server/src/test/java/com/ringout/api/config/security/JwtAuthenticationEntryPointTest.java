package com.ringout.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.config.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

  @Mock
  private JwtProvider jwtProvider;

  private JwtAuthenticationEntryPoint entryPoint;

  @BeforeEach
  void setUp() {
    entryPoint = new JwtAuthenticationEntryPoint(jwtProvider, JsonMapper.builder().build());
  }

  @Test
  void 만료된_액세스토큰이면_AUTH401을_응답한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer expired-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    given(jwtProvider.isExpiredToken("expired-token")).willReturn(true);

    // when
    entryPoint.commence(request, response, null);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":\"AUTH401\"");
  }

  @Test
  void Authorization_헤더가_없으면_COMMON401을_응답한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    entryPoint.commence(request, response, null);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":\"COMMON401\"");
    assertThat(response.getContentAsString()).contains("\"message\":\"인증되지 않은 사용자입니다.\"");
    assertThat(response.getContentAsString()).contains("\"result\":null");
    verify(jwtProvider, never()).isExpiredToken(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 토큰이_있지만_만료가_아니면_COMMON401을_응답한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer tampered-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    given(jwtProvider.isExpiredToken("tampered-token")).willReturn(false);

    // when
    entryPoint.commence(request, response, null);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":\"COMMON401\"");
  }
}
