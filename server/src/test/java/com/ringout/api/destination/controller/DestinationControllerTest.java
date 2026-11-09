package com.ringout.api.destination.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.service.DestinationService;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.Role;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    class 목적지_생성_응답 {

        @Test
        void 문자열_목적지_id를_받아_201로_응답한다() throws Exception {
            // given
            given(destinationService.createDestination(1L, "dest-1", "헬스장", 37.4979, 127.0276))
                .willReturn(new DestinationCreateResponse("dest-1"));

            // when
            var result = mockMvc.perform(post("/api/v1/destinations")
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destinationId":"dest-1","alias":"헬스장","latitude":37.4979,"longitude":127.0276}
                    """));

            // then
            result.andExpectAll(
                status().isCreated(),
                jsonPath("$.code").value("DESTINATION201"),
                jsonPath("$.result.destinationId").value("dest-1")
            );
        }

        @Test
        void 목적지_id가_없으면_COMMON400을_반환한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(post("/api/v1/destinations")
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"alias":"헬스장","latitude":37.4979,"longitude":127.0276}
                    """));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verify(destinationService, never()).createDestination(any(), any(), any(), any(), any());
        }

        @Test
        void 중복된_목적지_id는_DESTINATION409를_반환한다() throws Exception {
            // given
            given(destinationService.createDestination(1L, "dest-1", "헬스장", 37.4979, 127.0276))
                .willThrow(new GeneralException(DestinationErrorStatus.DESTINATION_ID_DUPLICATED));

            // when
            var result = mockMvc.perform(post("/api/v1/destinations")
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destinationId":"dest-1","alias":"헬스장","latitude":37.4979,"longitude":127.0276}
                    """));

            // then
            result.andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("DESTINATION409"),
                jsonPath("$.message").value("이미 존재하는 목적지입니다.")
            );
        }
    }
}
