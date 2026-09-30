package com.ringout.api.alarmmovement.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementsResponse;
import com.ringout.api.alarmmovement.service.AlarmMovementService;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.user.domain.Role;
import java.util.List;
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
    void 인증된_사용자의_이동_시작_요청에_MOVEMENT_STARTED_상태를_반환한다() throws Exception {
        // given
        AlarmMovementRequest request = new AlarmMovementRequest(135L, MovementAction.START_MOVEMENT);
        given(alarmMovementService.changeMovement(1L, 10L, request))
            .willReturn(new AlarmMovementResponse(MovementStatus.MOVEMENT_STARTED));

        // when
        var result = mockMvc.perform(post("/api/v1/rooms/10/movements")
            .with(user(new CustomUserDetails(1L, Role.USER)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"alarmId\":135,\"action\":\"START_MOVEMENT\"}"));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("MOVEMENT200"),
            jsonPath("$.message").value("이동 상태 변경에 성공했습니다."),
            jsonPath("$.result.status").value("MOVEMENT_STARTED")
        );
        verify(alarmMovementService).changeMovement(1L, 10L, request);
    }

    @Test
    void 인증된_사용자에게_정렬된_모임_회원_이동_상태를_반환한다() throws Exception {
        // given
        MemberMovementsResponse response = new MemberMovementsResponse(List.of(
            new MemberMovementResponse(2L, "가나다", MovementStatus.IDLE),
            new MemberMovementResponse(3L, "Alice", MovementStatus.MOVEMENT_STARTED),
            new MemberMovementResponse(4L, "123", MovementStatus.ARRIVED)
        ));
        given(alarmMovementService.getMemberMovements(1L, 10L)).willReturn(response);

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/10/members/movements")
            .with(user(new CustomUserDetails(1L, Role.USER))));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("ROOM200"),
            jsonPath("$.message").value("모임 회원 상태 조회에 성공했습니다."),
            jsonPath("$.result.members[0].userId").value(2L),
            jsonPath("$.result.members[0].nickname").value("가나다"),
            jsonPath("$.result.members[0].status").value("IDLE"),
            jsonPath("$.result.members[1].status").value("MOVEMENT_STARTED"),
            jsonPath("$.result.members[2].status").value("ARRIVED")
        );
        verify(alarmMovementService).getMemberMovements(1L, 10L);
    }

    @Test
    void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
        // given

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/10/members/movements"));

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
