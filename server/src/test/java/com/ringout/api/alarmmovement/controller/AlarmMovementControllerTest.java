package com.ringout.api.alarmmovement.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.service.AlarmMovementService;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlarmMovementController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AlarmMovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlarmMovementService alarmMovementService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 인증된_사용자의_이동_시작_요청에_STARTED_상태를_반환한다() throws Exception {
        // given
        AlarmMovementRequest request = new AlarmMovementRequest(135L, MovementAction.START);
        given(alarmMovementService.changeMovement(1L, 10L, request))
            .willReturn(new AlarmMovementResponse(MovementStatus.STARTED));

        // when
        var result = mockMvc.perform(post("/api/v1/rooms/10/movements")
            .with(user(new CustomUserDetails(1L, Role.USER)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"alarmId\":135,\"action\":\"START\"}"));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("MOVEMENT200"),
            jsonPath("$.message").value("이동 상태 변경에 성공했습니다."),
            jsonPath("$.result.status").value("STARTED")
        );
        verify(alarmMovementService).changeMovement(1L, 10L, request);
    }

}
