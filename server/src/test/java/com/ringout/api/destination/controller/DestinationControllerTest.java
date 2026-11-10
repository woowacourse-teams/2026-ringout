package com.ringout.api.destination.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
import com.ringout.api.destination.service.DestinationService;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.Role;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DestinationController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class DestinationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationService destinationService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Nested
    class 목적지_삭제_응답 {

        @Test
        void 삭제_요청이_처리되면_200과_null_result를_반환한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(delete("/api/v1/destinations/10")
                .with(user(new CustomUserDetails(1L, Role.USER))));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("DESTINATION200"),
                jsonPath("$.message").value("삭제되었습니다."),
                jsonPath("$.result").value(nullValue())
            );
            verify(destinationService).deleteDestination(1L, "10");
        }

        @Test
        void 문자열_목적지_id를_그대로_서비스에_전달한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(delete("/api/v1/destinations/dest-abc")
                .with(user(new CustomUserDetails(1L, Role.USER))));

            // then
            result.andExpect(status().isOk());
            verify(destinationService).deleteDestination(1L, "dest-abc");
        }

        @Test
        void 올바르지_않은_목적지_id는_DESTINATION400을_반환한다() throws Exception {
            // given
            String destinationId = "1234567890123456789012345678901234567";
            willThrow(new GeneralException(DestinationErrorStatus.DESTINATION_DELETE_ID_INVALID))
                .given(destinationService).deleteDestination(1L, destinationId);

            // when
            var result = mockMvc.perform(delete("/api/v1/destinations/" + destinationId)
                .with(user(new CustomUserDetails(1L, Role.USER))));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.isSuccess").value(false),
                jsonPath("$.code").value("DESTINATION400"),
                jsonPath("$.message").value("목적지 아이디가 올바르지 않습니다."),
                jsonPath("$.result").value(nullValue())
            );
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(delete("/api/v1/destinations/10"));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.isSuccess").value(false),
                jsonPath("$.code").value("AUTH401")
            );
            verify(destinationService, never()).deleteDestination(any(), any());
        }
    }

    @Nested
    class 목적지_upsert_응답 {

        private static final String UPSERT_URL = "/api/v1/destinations/destination-001";
        private static final String REQUEST_BODY = """
            {"alias":"헬스장","latitude":37.4979,"longitude":127.0276,"destinationVersion":2}
            """;

        @Test
        void 경로의_목적지_id와_body로_upsert하고_200과_서버에_저장된_목적지를_반환한다() throws Exception {
            // given
            given(destinationService.upsertDestination(any(), any(), any())).willReturn(
                new DestinationUpsertResponse("destination-001", "학교", 37.5502, 127.0738, 5L));

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isOk(),
                header().doesNotExist(HttpHeaders.LOCATION),
                jsonPath("$.code").value("DESTINATION200"),
                jsonPath("$.message").value("목적지 동기화에 성공했습니다."),
                jsonPath("$.result.destinationId").value("destination-001"),
                jsonPath("$.result.alias").value("학교"),
                jsonPath("$.result.latitude").value(37.5502),
                jsonPath("$.result.longitude").value(127.0738),
                jsonPath("$.result.destinationVersion").value(5)
            );
            verify(destinationService).upsertDestination(1L, "destination-001",
                new DestinationUpsertRequest("헬스장", 37.4979, 127.0276, 2L));
        }

        @Test
        void 목적지_버전이_Long이_아니면_COMMON400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"alias":"헬스장","latitude":37.4979,"longitude":127.0276,"destinationVersion":"abc"}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verify(destinationService, never()).upsertDestination(any(), any(), any());
        }

        @Test
        void 서비스_검증에_실패하면_DESTINATION400을_반환한다() throws Exception {
            // given
            given(destinationService.upsertDestination(any(), any(), any()))
                .willThrow(new GeneralException(DestinationErrorStatus.DESTINATION_VERSION_INVALID));

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"alias":"헬스장","latitude":37.4979,"longitude":127.0276}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("DESTINATION400"),
                jsonPath("$.message").value("목적지 버전 값이 올바르지 않습니다."),
                jsonPath("$.result").value(nullValue())
            );
        }

        @Test
        void 경로의_목적지_id가_올바르지_않으면_DESTINATION400을_반환한다() throws Exception {
            // given
            given(destinationService.upsertDestination(any(), any(), any()))
                .willThrow(new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID));

            // when
            var result = mockMvc.perform(put("/api/v1/destinations/{destinationId}", "dest 1")
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("DESTINATION400"),
                jsonPath("$.message").value("목적지 ID가 올바르지 않습니다.")
            );
            verify(destinationService).upsertDestination(1L, "dest 1",
                new DestinationUpsertRequest("헬스장", 37.4979, 127.0276, 2L));
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH401")
            );
            verify(destinationService, never()).upsertDestination(any(), any(), any());
        }
    }
}
