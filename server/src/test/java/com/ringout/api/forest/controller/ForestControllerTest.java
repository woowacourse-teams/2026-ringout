package com.ringout.api.forest.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
import com.ringout.api.forest.service.ForestService;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.Role;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ForestController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ForestControllerTest {

    private static final String ONBOARDING_URL = "/api/v1/forest/onboarding";
    private static final String REQUEST_BODY = """
        {"completedAt":"2026-11-10T08:00:00+09:00"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ForestService forestService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Nested
    class 숲_온보딩_완료_응답 {

        @Test
        void 완료_시각을_새로_저장하면_201과_FOREST201을_반환한다() throws Exception {
            // given
            given(forestService.completeOnboarding(any(), any())).willReturn(new ForestOnboardingCompleteResult(
                new ForestOnboardingResponse(OffsetDateTime.parse("2026-11-10T08:00:00+09:00")), true));

            // when
            var result = mockMvc.perform(put(ONBOARDING_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isCreated(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("FOREST201"),
                jsonPath("$.message").value("숲 온보딩 완료에 성공했습니다."),
                jsonPath("$.result.onboardingCompletedAt").value("2026-11-10T08:00:00+09:00")
            );
            verify(forestService).completeOnboarding(eq(1L), argThat((ForestOnboardingCompleteRequest request) ->
                request.completedAt().isEqual(OffsetDateTime.parse("2026-11-10T08:00:00+09:00"))));
        }

        @Test
        void 이미_완료한_사용자면_200과_FOREST200으로_저장된_시각을_반환한다() throws Exception {
            // given
            given(forestService.completeOnboarding(any(), any())).willReturn(new ForestOnboardingCompleteResult(
                new ForestOnboardingResponse(OffsetDateTime.parse("2026-11-10T08:00:00+09:00")), false));

            // when
            var result = mockMvc.perform(put(ONBOARDING_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"completedAt":"2026-11-12T09:30:00+09:00"}
                    """));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("FOREST200"),
                jsonPath("$.message").value("숲 온보딩 동기화에 성공했습니다."),
                jsonPath("$.result.onboardingCompletedAt").value("2026-11-10T08:00:00+09:00")
            );
        }

        @Test
        void 완료_시각이_없으면_FOREST400을_반환한다() throws Exception {
            // given
            given(forestService.completeOnboarding(any(), any()))
                .willThrow(new GeneralException(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID));

            // when
            var result = mockMvc.perform(put(ONBOARDING_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.isSuccess").value(false),
                jsonPath("$.code").value("FOREST400"),
                jsonPath("$.message").value("온보딩 요청 정보가 올바르지 않습니다."),
                jsonPath("$.result").value(nullValue())
            );
        }

        @Test
        void 완료_시각_형식이_올바르지_않으면_COMMON400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(ONBOARDING_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"completedAt":"2026-11-10 08:00"}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verify(forestService, never()).completeOnboarding(any(), any());
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(ONBOARDING_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH401")
            );
            verify(forestService, never()).completeOnboarding(any(), any());
        }
    }
}
