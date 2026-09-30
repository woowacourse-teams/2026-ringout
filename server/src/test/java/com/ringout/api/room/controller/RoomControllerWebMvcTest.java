package com.ringout.api.room.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class RoomControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 비로그인_사용자가_모임방_목록을_조회할_수_있다() throws Exception {
        // given
        given(roomService.getRooms(null)).willReturn(new RoomListResponse(List.of()));

        // when
        var result = mockMvc.perform(get("/api/v1/rooms"));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("ROOM200"),
            jsonPath("$.message").value("방 목록 조회에 성공했습니다."),
            jsonPath("$.result.rooms").isEmpty()
        );
        verify(roomService).getRooms(null);
    }

    @Test
    void 유효하지_않은_Bearer_토큰도_비로그인_사용자처럼_목록을_조회한다() throws Exception {
        // given
        given(jwtProvider.isValid("invalid-token")).willReturn(false);
        given(roomService.getRooms(null)).willReturn(new RoomListResponse(List.of()));

        // when
        var result = mockMvc.perform(get("/api/v1/rooms")
            .header("Authorization", "Bearer invalid-token"));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("ROOM200"),
            jsonPath("$.result.rooms").isEmpty()
        );
        verify(roomService).getRooms(null);
    }

    @Test
    void 비로그인_사용자는_모임방_상세_정보를_조회할_수_없다() throws Exception {
        // when
        var result = mockMvc.perform(get("/api/v1/rooms/1"));

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
