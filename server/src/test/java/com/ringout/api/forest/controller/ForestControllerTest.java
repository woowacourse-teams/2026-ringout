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
import com.ringout.api.forest.domain.TreeKind;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestTreePlantResult;
import com.ringout.api.forest.dto.response.ForestTreeResponse;
import com.ringout.api.forest.service.ForestService;
import com.ringout.api.forest.service.ForestTreeService;
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
    private ForestTreeService forestTreeService;

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

    @Nested
    class 나무_심기_응답 {

        private static final String TREE_URL = "/api/v1/forest/trees/1";
        private static final String TREE_REQUEST_BODY = """
            {"kind":"pine","plantedAt":"2026-11-05T07:48:10+09:00"}
            """;

        @Test
        void 나무를_새로_저장하면_201과_FOREST201을_반환한다() throws Exception {
            // given
            given(forestTreeService.plantTree(any(), any(), any())).willReturn(new ForestTreePlantResult(
                new ForestTreeResponse(1, TreeKind.PINE, 7, OffsetDateTime.parse("2026-11-05T07:48:10+09:00"), null),
                true));

            // when
            var result = mockMvc.perform(put(TREE_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TREE_REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isCreated(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("FOREST201"),
                jsonPath("$.message").value("나무 저장에 성공했습니다."),
                jsonPath("$.result.growthCycle").value(1),
                jsonPath("$.result.kind").value("pine"),
                jsonPath("$.result.slotIndex").value(7),
                jsonPath("$.result.plantedAt").value("2026-11-05T07:48:10+09:00"),
                jsonPath("$.result.acknowledgedAt").value(nullValue())
            );
            verify(forestTreeService).plantTree(eq(1L), eq("1"), argThat((ForestTreePlantRequest request) ->
                request.kind() == TreeKind.PINE
                    && request.plantedAt().isEqual(OffsetDateTime.parse("2026-11-05T07:48:10+09:00"))));
        }

        @Test
        void 이미_저장된_회차면_200과_FOREST200으로_저장된_나무를_반환한다() throws Exception {
            // given
            given(forestTreeService.plantTree(any(), any(), any())).willReturn(new ForestTreePlantResult(
                new ForestTreeResponse(1, TreeKind.BIG_TREE, 3, OffsetDateTime.parse("2026-11-05T07:48:10+09:00"),
                    OffsetDateTime.parse("2026-11-10T08:01:00+09:00")),
                false));

            // when
            var result = mockMvc.perform(put(TREE_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TREE_REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("FOREST200"),
                jsonPath("$.message").value("나무 동기화에 성공했습니다."),
                jsonPath("$.result.kind").value("big_tree"),
                jsonPath("$.result.slotIndex").value(3),
                jsonPath("$.result.acknowledgedAt").value("2026-11-10T08:01:00+09:00")
            );
        }

        @Test
        void 정의되지_않은_나무_종류는_종류_없이_서비스에_전달한다() throws Exception {
            // given
            given(forestTreeService.plantTree(any(), any(), any()))
                .willThrow(new GeneralException(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));

            // when
            var result = mockMvc.perform(put(TREE_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"maple","plantedAt":"2026-11-05T07:48:10+09:00"}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("FOREST400"),
                jsonPath("$.message").value("나무 요청 정보가 올바르지 않습니다.")
            );
            verify(forestTreeService).plantTree(eq(1L), eq("1"), argThat((ForestTreePlantRequest request) ->
                request.kind() == null));
        }

        @Test
        void 회차가_올바르지_않으면_FOREST400을_반환한다() throws Exception {
            // given
            given(forestTreeService.plantTree(any(), any(), any()))
                .willThrow(new GeneralException(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID));

            // when
            var result = mockMvc.perform(put("/api/v1/forest/trees/abc")
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TREE_REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("FOREST400"),
                jsonPath("$.message").value("나무 회차가 올바르지 않습니다.")
            );
            verify(forestTreeService).plantTree(eq(1L), eq("abc"), any());
        }

        @Test
        void 획득_기록이_서버_기록과_다르면_409와_FOREST409를_반환한다() throws Exception {
            // given
            given(forestTreeService.plantTree(any(), any(), any()))
                .willThrow(new GeneralException(ForestErrorStatus.FOREST_TREE_RECORD_MISMATCH));

            // when
            var result = mockMvc.perform(put(TREE_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TREE_REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isConflict(),
                jsonPath("$.isSuccess").value(false),
                jsonPath("$.code").value("FOREST409"),
                jsonPath("$.message").value("나무 획득 기록이 서버 기록과 일치하지 않습니다."),
                jsonPath("$.result").value(nullValue())
            );
        }

        @Test
        void 획득_시각_형식이_올바르지_않으면_COMMON400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(TREE_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"pine","plantedAt":"2026-11-05 07:48"}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verify(forestTreeService, never()).plantTree(any(), any(), any());
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(TREE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TREE_REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH401")
            );
            verify(forestTreeService, never()).plantTree(any(), any(), any());
        }
    }
}
