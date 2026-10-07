package com.ringout.api.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomDetailResponse;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomMemberResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.room.service.RoomRecordService;
import com.ringout.api.user.domain.Role;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class RoomControllerTest {

    private RoomService roomService;
    private RoomRecordService roomRecordService;
    private RoomController roomController;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        roomRecordService = mock(RoomRecordService.class);
        roomController = new RoomController(roomService, roomRecordService);
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
                List.of(new RoomMemberResponse(1L, "가나다", null, "OWNER"))
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

    @Nested
    class 선택적_인증_모임방_목록_조회_응답 {

        @Test
        void 로그인한_사용자에게_목록_성공_응답을_반환한다() {
            // given
            Long userId = 1L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
            RoomListResponse serviceResponse = new RoomListResponse(List.of());
            given(roomService.getRooms(userId)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomListResponse>> response = roomController.getRooms(userDetails);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("방 목록 조회에 성공했습니다.");
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).getRooms(userId);
        }

        @Test
        void 비로그인_사용자에게도_목록_성공_응답을_반환한다() {
            // given
            RoomListResponse serviceResponse = new RoomListResponse(List.of());
            given(roomService.getRooms(null)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomListResponse>> response = roomController.getRooms(null);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).getRooms(null);
        }
    }

    @Nested
    class 인증된_참여자_모임방_상세_조회_응답 {

        @Test
        void 상세_조회_성공_응답을_반환한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
            RoomDetailResponse serviceResponse = new RoomDetailResponse(
                roomId,
                "아침 운동 모임",
                "매주 함께 운동하고 인증하는 모임입니다.",
                "/images/default-room.png",
                List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
                "08:00",
                1,
                "OWNER",
                null,
                List.of(new RoomMemberResponse(userId, "가나다", null, "OWNER"))
            );
            given(roomService.getRoom(userId, roomId)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomDetailResponse>> response = roomController.getRoom(userDetails, roomId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("방 상세 정보 조회에 성공했습니다.");
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).getRoom(userId, roomId);
        }
    }

    @Nested
    class 인증된_사용자_모임방_참여_응답 {

        @Test
        void 참여_성공_시_세부_정보_형식의_CREATED_응답을_반환한다() {
            // given
            Long userId = 2L;
            Long roomId = 10L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
            RoomDetailResponse serviceResponse = new RoomDetailResponse(
                roomId, "아침 운동 모임", "매주 함께 운동하고 인증하는 모임입니다.",
                "/images/default-room.png", List.of("MONDAY", "WEDNESDAY", "FRIDAY"), "08:00", 2,
                "MEMBER", null,
                List.of(
                    new RoomMemberResponse(1L, "방장", null, "OWNER"),
                    new RoomMemberResponse(userId, "참여자", null, "MEMBER")
                )
            );
            given(roomService.joinRoom(userId, roomId)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomDetailResponse>> response = roomController.joinRoom(userDetails, roomId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM201");
            assertThat(response.getBody().getMessage()).isEqualTo("모임 방 참여에 성공했습니다.");
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).joinRoom(userId, roomId);
        }
    }

    @Nested
    class 인증된_방장_모임방_수정_응답 {

        @Test
        void multipart_수정_성공_응답을_반환한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
            MockMultipartFile image = new MockMultipartFile("image", "room.png", "image/png", new byte[]{1});
            RoomUpdateRequest request = new RoomUpdateRequest("새로운 아침 운동 모임", "", image);
            RoomUpdateResponse serviceResponse = new RoomUpdateResponse(
                roomId,
                "새로운 아침 운동 모임",
                "",
                "/images/default-room.png"
            );
            given(roomService.updateRoom(userId, roomId, request)).willReturn(serviceResponse);

            // when
            ResponseEntity<CustomResponse<RoomUpdateResponse>> response = roomController.updateRoom(
                userDetails,
                roomId,
                request
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("방 정보 수정에 성공했습니다.");
            assertThat(response.getBody().getResult()).isEqualTo(serviceResponse);
            verify(roomService).updateRoom(userId, roomId, request);
        }
    }

    @Nested
    class 인증된_방장_모임방_삭제_응답 {

        @Test
        void 삭제_성공_응답을_반환한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);

            // when
            ResponseEntity<CustomResponse<Void>> response = roomController.deleteRoom(userDetails, roomId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("방 삭제에 성공했습니다.");
            assertThat(response.getBody().getResult()).isNull();
            verify(roomService).deleteRoom(userId, roomId);
        }
    }

    @Nested
    class 인증된_참여자_모임방_탈퇴_응답 {

        @Test
        void 탈퇴_성공_응답을_반환한다() {
            // given
            Long userId = 2L;
            Long roomId = 10L;
            CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);

            // when
            ResponseEntity<CustomResponse<Void>> response = roomController.leaveRoom(userDetails, roomId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("모임 방 탈퇴에 성공했습니다.");
            assertThat(response.getBody().getResult()).isNull();
            verify(roomService).leaveRoom(userId, roomId);
        }
    }

    @Nested
    class 인증된_방장_회원_추방_응답 {

        @Test
        void 회원_추방_성공_응답을_반환한다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            RoomKickRequest request = new RoomKickRequest(2L);
            CustomUserDetails userDetails = new CustomUserDetails(hostUserId, Role.USER);

            // when
            ResponseEntity<CustomResponse<Void>> response = roomController.kickMember(userDetails, roomId, request);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIsSuccess()).isTrue();
            assertThat(response.getBody().getCode()).isEqualTo("ROOM200");
            assertThat(response.getBody().getMessage()).isEqualTo("회원 추방에 성공했습니다.");
            assertThat(response.getBody().getResult()).isNull();
            verify(roomService).kickMember(hostUserId, roomId, request);
        }
    }
}
