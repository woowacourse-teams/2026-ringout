package com.ringout.api.room.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomManagementMemberResponse;
import com.ringout.api.room.dto.response.RoomMembersResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.room.service.RoomRecordService;
import com.ringout.api.user.domain.Role;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private RoomRecordService roomRecordService;

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

    @Test
    void 비로그인_사용자는_모임방에서_탈퇴할_수_없다() throws Exception {
        // given

        // when
        var result = mockMvc.perform(delete("/api/v1/rooms/1/members"));

        // then
        result.andExpectAll(
            status().isUnauthorized(),
            jsonPath("$.isSuccess").value(false),
            jsonPath("$.code").value("AUTH401"),
            jsonPath("$.message").value("인증되지 않은 사용자입니다."),
            jsonPath("$.result").doesNotExist()
        );
        verifyNoInteractions(roomService);
    }

    @Test
    void 방장이_회원_관리용_회원_목록을_조회할_수_있다() throws Exception {
        // given
        Long roomId = 10L;
        RoomMembersResponse response = new RoomMembersResponse(List.of(
            new RoomManagementMemberResponse(1L, "가나다", null,
                LocalDateTime.of(2026, 9, 20, 10, 30), "OWNER")
        ));
        given(roomService.getMembersForManagement(1L, roomId)).willReturn(response);

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/{roomId}/members", roomId)
            .with(user(new CustomUserDetails(1L, Role.USER))));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("MEMBER200"),
            jsonPath("$.message").value("모임 회원 조회에 성공했습니다."),
            jsonPath("$.result.members[0].userId").value(1),
            jsonPath("$.result.members[0].joinedAt").value("2026-09-20T10:30:00"),
            jsonPath("$.result.members[0].membershipRole").value("OWNER")
        );
        verify(roomService).getMembersForManagement(1L, roomId);
    }

    @Test
    void 비로그인_사용자는_회원_관리용_회원_목록을_조회할_수_없다() throws Exception {
        // given

        // when
        var result = mockMvc.perform(get("/api/v1/rooms/1/members"));

        // then
        result.andExpectAll(
            status().isUnauthorized(),
            jsonPath("$.isSuccess").value(false),
            jsonPath("$.code").value("AUTH401"),
            jsonPath("$.message").value("인증되지 않은 사용자입니다.")
        );
        verifyNoInteractions(roomService);
    }

    @Test
    void 활동_요일과_시간을_multipart_수정_요청으로_바인딩한다() throws Exception {
        // given
        Long roomId = 10L;
        RoomUpdateResponse response = new RoomUpdateResponse(roomId, "아침 운동 모임", null, null);
        given(roomService.updateRoom(eq(1L), eq(roomId), any())).willReturn(response);

        // when
        var result = mockMvc.perform(multipart("/api/v1/rooms/{roomId}", roomId)
            .param("activityDays", "TUESDAY", "THURSDAY")
            .param("activityTime", "19:30")
            .with(request -> {
                request.setMethod("PATCH");
                return request;
            })
            .with(user(new CustomUserDetails(1L, Role.USER))));

        // then
        result.andExpectAll(
            status().isOk(),
            jsonPath("$.isSuccess").value(true),
            jsonPath("$.code").value("ROOM200")
        );
        verify(roomService).updateRoom(eq(1L), eq(roomId),
            argThat(request -> request.activityDays().equals(List.of(
                ActivityDay.TUESDAY,
                ActivityDay.THURSDAY
            )) && request.activityTime().equals(LocalTime.of(19, 30))));
    }
}
