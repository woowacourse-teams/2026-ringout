package com.ringout.api.room.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class RoomRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 인증된_회원이_기록이_없는_활동_날짜를_조회하면_빈_회원_기록을_반환한다() throws Exception {
        // given
        Long roomId = 1L;
        String date = "2026-09-16";

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/{roomId}/records", roomId)
            .param("date", date)
            .with(user(new CustomUserDetails(1L, Role.USER))));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("RECORD200"),
            jsonPath("$.message").value("모임 회원 기록 조회에 성공했습니다."),
            jsonPath("$.result.memberRecords").isArray(),
            jsonPath("$.result.memberRecords").isEmpty()
        );
    }

    @Test
    void 날짜_형식이_올바르지_않으면_RECORD400을_반환한다() throws Exception {
        // given
        Long roomId = 1L;
        String invalidDate = "2026/09/16";

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/{roomId}/records", roomId)
            .param("date", invalidDate)
            .with(user(new CustomUserDetails(1L, Role.USER))));

        // then
        result.andExpectAll(
            status().isBadRequest(),
            jsonPath("$.isSuccess").value(false),
            jsonPath("$.code").value("RECORD400"),
            jsonPath("$.message").value("조회 날짜의 형식이 올바르지 않습니다."),
            jsonPath("$.result").doesNotExist()
        );
    }

    @Test
    void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
        // given
        Long roomId = 1L;

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/{roomId}/records", roomId)
            .param("date", "2026-09-16"));

        // then
        result.andExpectAll(
            status().isUnauthorized(),
            jsonPath("$.isSuccess").value(false),
            jsonPath("$.code").value("AUTH401"),
            jsonPath("$.message").value("인증되지 않은 사용자입니다."),
            jsonPath("$.result").doesNotExist()
        );
    }
}
