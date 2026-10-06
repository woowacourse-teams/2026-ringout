package com.ringout.api.terms.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.dto.response.TermsAgreementStatusResponse;
import com.ringout.api.terms.dto.response.TermsAgreementsResponse;
import com.ringout.api.terms.service.TermsService;
import com.ringout.api.terms.status.TermsErrorStatus;
import com.ringout.api.user.domain.Role;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TermsController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class TermsControllerTest {

    private static final Long USER_ID = 1L;
    private static final CustomUserDetails USER = new CustomUserDetails(USER_ID, Role.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TermsService termsService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Nested
    class 최신_약관_동의_여부_조회 {

        @Test
        void 약관별_동의_상태와_전체_동의_여부를_반환한다() throws Exception {
            // given
            given(termsService.getTermsAgreements(USER_ID)).willReturn(TermsAgreementsResponse.from(List.of(
                new TermsAgreementStatusResponse(TermsType.SERVICE, 3L, LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-10T14:32:11+09:00"), true),
                new TermsAgreementStatusResponse(TermsType.PRIVACY, 2L, LocalDate.of(2026, 8, 1),
                    null, null, true)
            )));

            // when
            var result = mockMvc.perform(get("/api/v1/terms/agreements").with(user(USER)));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("COMMON200"),
                jsonPath("$.message").value("약관 동의 조회 요청에 성공했습니다."),
                jsonPath("$.result.allAgreed").value(false),
                jsonPath("$.result.agreements[0].type").value("SERVICE"),
                jsonPath("$.result.agreements[0].termsId").value(3),
                jsonPath("$.result.agreements[0].latestVersion").value("2026-09-01"),
                jsonPath("$.result.agreements[0].agreedVersion").value("2026-08-01"),
                jsonPath("$.result.agreements[0].agreedAt").value("2026-08-10T14:32:11+09:00"),
                jsonPath("$.result.agreements[0].needsReagreement").value(true),
                jsonPath("$.result.agreements[1].type").value("PRIVACY"),
                jsonPath("$.result.agreements[1].agreedVersion").isEmpty(),
                jsonPath("$.result.agreements[1].agreedAt").isEmpty()
            );
        }

        @Test
        void 인증되지_않은_사용자는_조회할_수_없다() throws Exception {
            // when
            var result = mockMvc.perform(get("/api/v1/terms/agreements"));

            // then
            result.andExpect(status().isUnauthorized());
            verifyNoInteractions(termsService);
        }

        @Test
        void 시행_중인_약관이_없으면_500을_반환한다() throws Exception {
            // given
            given(termsService.getTermsAgreements(USER_ID))
                .willThrow(new GeneralException(TermsErrorStatus.EFFECTIVE_TERMS_MISSING));

            // when
            var result = mockMvc.perform(get("/api/v1/terms/agreements").with(user(USER)));

            // then
            result.andExpectAll(
                status().isInternalServerError(),
                jsonPath("$.code").value("TERMS500")
            );
        }
    }
}
