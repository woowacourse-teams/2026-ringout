package com.ringout.api.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.dto.response.RoomMemberResponse;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.user.domain.Role;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RoomControllerTest {

    private RoomService roomService;
    private RoomController roomController;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        roomController = new RoomController(roomService);
    }

    @Nested
    class 인증된_사용자_모임방_생성_응답 {

        @Test
        void 생성_성공_응답을_반환한다() {
            // given
            Long userId = 1L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
            RoomCreateRequest request = new RoomCreateRequest(
                "아침 운동 모임",
                "매주 함께 운동하고 인증하는 모임입니다.",
                List.of(ActivityDay.MONDAY, ActivityDay.WEDNESDAY, ActivityDay.FRIDAY),
                LocalTime.of(8, 0)
            );
            RoomCreateResponse serviceResponse = new RoomCreateResponse(
                10L,
                "아침 운동 모임",
                "매주 함께 운동하고 인증하는 모임입니다.",
                "/images/default-room.png",
                List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
                "08:00",
                1,
                "OWNER",
                null,
                List.of(new RoomMemberResponse(1L, "가나다", null))
            );
            given(roomService.createRoom(userId, request)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomCreateResponse>> response =
                roomController.createRoom(userDetails, request);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM201");
            assertThat(response.getBody().getMessage()).isEqualTo("모임 방이 생성되었습니다.");
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).createRoom(userId, request);
        }
    }
}
