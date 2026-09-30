package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomDetailResponse;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomMemberResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.repository.RoomBlackListRepository;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.Nickname;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUserRepository roomUserRepository;

    @Mock
    private RoomBlackListRepository roomBlackListRepository;

    @Mock
    private UserRepository userRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomUserRepository, roomBlackListRepository, userRepository);
    }

    @Nested
    class 인증된_사용자_모임방_생성_처리 {

        @Test
        void 생성된_모임방과_방장_회원_정보를_반환한다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            RoomCreateRequest request = validRequest();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.save(any(Room.class))).willAnswer(invocation -> {
                Room room = invocation.getArgument(0);
                ReflectionTestUtils.setField(room, "id", 10L);
                ReflectionTestUtils.setField(room, "created_at", LocalDateTime.of(2026, 9, 23, 16, 30));
                return room;
            });
            given(roomUserRepository.save(any(RoomUser.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            RoomCreateResponse response = roomService.createRoom(userId, request);

            // then
            assertThat(response.roomId()).isEqualTo(10L);
            assertThat(response.name()).isEqualTo("아침 운동 모임");
            assertThat(response.description()).isEqualTo("매주 함께 운동하고 인증하는 모임입니다.");
            assertThat(response.activityDays()).containsExactly("MONDAY", "WEDNESDAY", "FRIDAY");
            assertThat(response.activityTime()).isEqualTo("08:00");
            assertThat(response.memberCount()).isEqualTo(1);
            assertThat(response.membershipRole()).isEqualTo("OWNER");
            assertThat(response.members()).hasSize(1);
            assertThat(response.members().get(0).userId()).isEqualTo(userId);
            assertThat(response.members().get(0).nickname()).isEqualTo("가나다");
            assertThat(response.members().get(0).profileImageUrl()).isNull();
            verify(roomRepository).save(any(Room.class));
            verify(roomUserRepository).save(any(RoomUser.class));
        }
    }

    @Nested
    class 모임방_최신_활동_정렬_처리 {

        @Test
        void 방_수정과_회원의_알람_기록_및_상태_변경_중_가장_최근_활동순으로_목록을_반환한다() {
            // given
            Long userId = 1L;
            Room roomUpdated = roomWithDetails(
                10L,
                "아침 운동 모임",
                "매주 함께 운동하고 인증하는 모임입니다.",
                List.of(ActivityDay.FRIDAY, ActivityDay.MONDAY),
                LocalTime.of(8, 0),
                "https://example.com/images/room-1.png",
                LocalDateTime.of(2026, 9, 20, 10, 30)
            );
            Room roomWithAlarmOccurrence = roomWithDetails(
                20L,
                "퇴근 후 러닝",
                null,
                List.of(ActivityDay.THURSDAY, ActivityDay.TUESDAY),
                LocalTime.of(19, 30),
                null,
                LocalDateTime.of(2026, 9, 18, 14, 20)
            );
            Room roomWithMovementStatus = roomWithDetails(
                30L,
                "점심 산책",
                null,
                List.of(ActivityDay.WEDNESDAY),
                LocalTime.of(12, 30),
                null,
                LocalDateTime.of(2026, 9, 17, 14, 20)
            );
            given(roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc()).willReturn(List.of(
                roomWithAlarmOccurrence, roomWithMovementStatus, roomUpdated
            ));
            given(roomUserRepository.countActiveByRoomId(10L)).willReturn(12);
            given(roomUserRepository.countActiveByRoomId(20L)).willReturn(5);
            given(roomUserRepository.countActiveByRoomId(30L)).willReturn(3);
            given(roomUserRepository.existsActiveByRoomIdAndUserId(10L, userId)).willReturn(true);
            given(roomUserRepository.existsActiveByRoomIdAndUserId(20L, userId)).willReturn(false);
            given(roomUserRepository.existsActiveByRoomIdAndUserId(30L, userId)).willReturn(false);

            // when
            RoomListResponse response = roomService.getRooms(userId);

            // then
            assertThat(response.rooms()).extracting("roomId").containsExactly(20L, 30L, 10L);
            assertThat(response.rooms().get(0).isJoined()).isFalse();
            assertThat(response.rooms().get(1).memberCount()).isEqualTo(3);
            assertThat(response.rooms().get(2).isJoined()).isTrue();
            verify(roomRepository).findAllActiveOrderByLatestActivityAtDescIdAsc();
        }

        @Test
        void 비로그인_사용자는_참여_여부를_false로_반환한다() {
            // given
            Room room = roomWithDetails(
                10L,
                "아침 운동 모임",
                null,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0),
                null,
                LocalDateTime.of(2026, 9, 20, 10, 30)
            );
            given(roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc()).willReturn(List.of(room));
            given(roomUserRepository.countActiveByRoomId(10L)).willReturn(1);

            // when
            RoomListResponse response = roomService.getRooms(null);

            // then
            assertThat(response.rooms()).singleElement().extracting("isJoined").isEqualTo(false);
            verify(roomUserRepository, never()).existsActiveByRoomIdAndUserId(any(), any());
        }

        @Test
        void 조회할_모임방이_없으면_빈_목록을_반환한다() {
            // given
            given(roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc()).willReturn(List.of());

            // when
            RoomListResponse response = roomService.getRooms(null);

            // then
            assertThat(response.rooms()).isEmpty();
            verify(roomUserRepository, never()).countActiveByRoomId(any());
        }
    }

    @Nested
    class 인증_실패_모임방_생성_처리 {

        @Test
        void 사용자_id가_null이면_생성할_수_없다() {
            // given
            Long userId = null;
            RoomCreateRequest request = validRequest();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(userId, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).save(any());
            verify(roomUserRepository, never()).save(any());
        }

        @Test
        void 존재하지_않는_사용자는_생성할_수_없다() {
            // given
            Long userId = 1L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(userId, validRequest()));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).save(any());
            verify(roomUserRepository, never()).save(any());
        }
    }

    @Nested
    class 모임방_생성_요청_유효성_규칙 {

        @Test
        void 모임방_이름이_올바르지_않으면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동!",
                "소개",
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NAME_INVALID));
        }

        @Test
        void 모임방_소개가_올바르지_않으면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동 모임",
                " ",
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DESCRIPTION_INVALID));
        }

        @Test
        void 활동_요일이_없으면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동 모임",
                null,
                List.of(),
                LocalTime.of(8, 0)
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAYS_REQUIRED));
        }

        @Test
        void null_활동_요일이면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동 모임",
                null,
                java.util.Arrays.asList(ActivityDay.MONDAY, null),
                LocalTime.of(8, 0)
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAY_INVALID));
        }

        @Test
        void 활동_요일이_중복되면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동 모임",
                null,
                List.of(ActivityDay.MONDAY, ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAY_INVALID));
        }

        @Test
        void 활동_시간이_없으면_생성할_수_없다() {
            // given
            RoomCreateRequest request = new RoomCreateRequest(
                "운동 모임",
                null,
                List.of(ActivityDay.MONDAY),
                null
            );
            givenValidUser();

            // when
            Throwable thrown = catchThrowable(() -> roomService.createRoom(1L, request));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_TIME_INVALID));
        }
    }

    @Nested
    class 방장_모임방_수정_처리 {

        @Test
        void 이름과_소개를_수정하고_기본_이미지_URL을_반환한다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            Room room = roomWithHost(userId, 10L);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findById(10L)).willReturn(Optional.of(room));
            RoomUpdateRequest request = new RoomUpdateRequest("새로운 아침 운동 모임", "", null);

            // when
            RoomUpdateResponse response = roomService.updateRoom(userId, 10L, request);

            // then
            assertThat(response.roomId()).isEqualTo(10L);
            assertThat(response.name()).isEqualTo("새로운 아침 운동 모임");
            assertThat(response.description()).isEmpty();
            assertThat(response.imageUrl()).isEqualTo("/images/default-room.png");
        }

        @Test
        void 유효한_이미지만_전달하면_기본_이미지_URL을_반환한다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            Room room = roomWithHost(userId, 10L);
            MockMultipartFile image = new MockMultipartFile("image", "room.png", "image/png",
                "image".getBytes(StandardCharsets.UTF_8));
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findById(10L)).willReturn(Optional.of(room));
            RoomUpdateRequest request = new RoomUpdateRequest(null, null, image);

            // when
            RoomUpdateResponse response = roomService.updateRoom(userId, 10L, request);

            // then
            assertThat(response.name()).isEqualTo("아침 운동 모임");
            assertThat(response.imageUrl()).isEqualTo("/images/default-room.png");
        }
    }

    @Nested
    class 모임방_수정_권한_검증 {

        @Test
        void 인증되지_않은_사용자는_모임방을_수정할_수_없다() {
            // given
            Long userId = null;

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest("새 이름", null, null)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).findById(10L);
        }

        @Test
        void 존재하지_않는_모임방은_수정할_수_없다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findById(10L)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest("새 이름", null, null)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NOT_FOUND));
        }

        @Test
        void 방장이_아니면_모임방을_수정할_수_없다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            Room room = roomWithHost(2L, 10L);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findById(10L)).willReturn(Optional.of(room));

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest("새 이름", null, null)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_FORBIDDEN));
        }
    }

    @Nested
    class 모임방_수정_요청_유효성_규칙 {

        @Test
        void 수정할_정보가_없으면_수정할_수_없다() {
            // given
            Long userId = 1L;
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest(null, null, null)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UPDATE_REQUIRED));
            verify(roomRepository, never()).findById(10L);
        }

        @Test
        void 빈_이미지_파일이면_수정할_수_없다() {
            // given
            Long userId = 1L;
            MockMultipartFile image = new MockMultipartFile("image", "room.png", "image/png", new byte[0]);
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest(null, null, image)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_IMAGE_INVALID));
            verify(roomRepository, never()).findById(10L);
        }

        @Test
        void 이미지_형식이_아니면_수정할_수_없다() {
            // given
            Long userId = 1L;
            MockMultipartFile image = new MockMultipartFile("image", "room.txt", "text/plain",
                "not an image".getBytes(StandardCharsets.UTF_8));
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            Throwable thrown = catchThrowable(() -> roomService.updateRoom(userId, 10L,
                new RoomUpdateRequest(null, null, image)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_IMAGE_INVALID));
            verify(roomRepository, never()).findById(10L);
        }
    }

    @Nested
    class 방장_모임방_삭제_처리 {

        @Test
        void 모임방과_블랙리스트를_soft_delete한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            User user = userWithId(userId, "가나다");
            Room room = roomWithHost(userId, roomId);
            RoomBlackList roomBlackList = RoomBlackList.of(room, userWithId(2L, "추방회원"));
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(roomBlackListRepository.findActiveByRoom(room)).willReturn(List.of(roomBlackList));

            // when
            roomService.deleteRoom(userId, roomId);

            // then
            assertThat(room.getDeletedAt()).isNotNull();
            assertThat(roomBlackList.getDeletedAt()).isNotNull();
            verify(roomRepository, never()).delete(room);
            verify(roomBlackListRepository, never()).delete(any(RoomBlackList.class));
        }
    }

    @Nested
    class 모임방_삭제_권한_검증 {

        @Test
        void 인증되지_않은_사용자는_모임방을_삭제할_수_없다() {
            // given
            Long userId = null;
            Long roomId = 10L;

            // when
            Throwable thrown = catchThrowable(() -> roomService.deleteRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).findActiveById(any());
            verify(roomBlackListRepository, never()).findActiveByRoom(any());
        }

        @Test
        void 존재하지_않는_사용자는_모임방을_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.deleteRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).findActiveById(any());
            verify(roomBlackListRepository, never()).findActiveByRoom(any());
        }

        @Test
        void 존재하지_않거나_삭제된_모임방은_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.deleteRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NOT_FOUND));
            verify(roomBlackListRepository, never()).findActiveByRoom(any());
        }

        @Test
        void 방장이_아니면_모임방을_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            Room room = roomWithHost(2L, roomId);
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));

            // when
            Throwable thrown = catchThrowable(() -> roomService.deleteRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DELETE_FORBIDDEN);
                    assertThat(exception.getErrorReasonHttpStatus().httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getErrorReasonHttpStatus().code()).isEqualTo("ROOM403");
                    assertThat(exception.getErrorReasonHttpStatus().message()).isEqualTo("모임 방을 삭제할 권한이 없습니다.");
                });
            verify(roomBlackListRepository, never()).findActiveByRoom(any());
        }
    }

    @Nested
    class 방장_회원_추방_처리 {

        @Test
        void 참여_중인_일반_회원을_soft_delete하고_블랙리스트에_등록한다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            Long targetUserId = 2L;
            User host = userWithId(hostUserId, "방장");
            User target = userWithId(targetUserId, "참여자");
            Room room = roomWithHost(hostUserId, roomId);
            RoomUser roomUser = RoomUser.of(target, room);
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(userRepository.findById(targetUserId)).willReturn(Optional.of(target));
            given(roomUserRepository.findActiveByRoomIdAndUserId(roomId, targetUserId))
                .willReturn(Optional.of(roomUser));

            // when
            roomService.kickMember(hostUserId, roomId, new RoomKickRequest(targetUserId));

            // then
            assertThat(roomUser.getDeletedAt()).isNotNull();
            verify(roomBlackListRepository).save(argThat(blackList ->
                blackList.getRoom().equals(room) && blackList.getUser().equals(target)));
            verify(roomUserRepository, never()).delete(roomUser);
        }
    }

    @Nested
    class 모임방_회원_추방_권한_및_상태_검증 {

        @Test
        void 인증되지_않은_사용자는_회원을_추방할_수_없다() {
            // given
            Long roomId = 10L;
            Long targetUserId = 2L;

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(null, roomId,
                new RoomKickRequest(targetUserId)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).findActiveById(any());
            verify(roomUserRepository, never()).findActiveByRoomIdAndUserId(any(), any());
        }

        @Test
        void 존재하지_않거나_삭제된_모임방에서는_회원을_추방할_수_없다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            User host = userWithId(hostUserId, "방장");
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(hostUserId, roomId,
                new RoomKickRequest(2L)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NOT_FOUND));
            verify(roomUserRepository, never()).findActiveByRoomIdAndUserId(any(), any());
        }

        @Test
        void 방장이_아니면_회원을_추방할_수_없다() {
            // given
            Long requesterUserId = 1L;
            Long roomId = 10L;
            User requester = userWithId(requesterUserId, "참여자");
            Room room = roomWithHost(2L, roomId);
            given(userRepository.findById(requesterUserId)).willReturn(Optional.of(requester));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(requesterUserId, roomId,
                new RoomKickRequest(3L)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_KICK_FORBIDDEN));
            verify(roomUserRepository, never()).findActiveByRoomIdAndUserId(any(), any());
        }

        @Test
        void 존재하지_않는_사용자는_추방할_수_없다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            Long targetUserId = 2L;
            User host = userWithId(hostUserId, "방장");
            Room room = roomWithHost(hostUserId, roomId);
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(userRepository.findById(targetUserId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(hostUserId, roomId,
                new RoomKickRequest(targetUserId)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(
                        com.ringout.api.user.status.UserErrorStatus.USER_NOT_FOUND));
            verify(roomUserRepository, never()).findActiveByRoomIdAndUserId(any(), any());
        }

        @Test
        void 모임에_참여하고_있지_않은_사용자는_추방할_수_없다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            Long targetUserId = 2L;
            User host = userWithId(hostUserId, "방장");
            User target = userWithId(targetUserId, "참여자");
            Room room = roomWithHost(hostUserId, roomId);
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(userRepository.findById(targetUserId)).willReturn(Optional.of(target));
            given(roomUserRepository.findActiveByRoomIdAndUserId(roomId, targetUserId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(hostUserId, roomId,
                new RoomKickRequest(targetUserId)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_MEMBER_NOT_FOUND));
            verify(roomBlackListRepository, never()).save(any(RoomBlackList.class));
        }

        @Test
        void 방장은_자신을_추방할_수_없다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            User host = userWithId(hostUserId, "방장");
            Room room = roomWithHost(hostUserId, roomId);
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));

            // when
            Throwable thrown = catchThrowable(() -> roomService.kickMember(hostUserId, roomId,
                new RoomKickRequest(hostUserId)));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_HOST_KICK_FORBIDDEN));
            verify(roomUserRepository, never()).findActiveByRoomIdAndUserId(any(), any());
        }
    }

    @Nested
    class 인증된_참여자_모임방_상세_조회 {

        @Test
        void 일반_참여자는_정렬된_현재_참여자와_MEMBER_관계를_포함한_상세_정보를_조회한다() {
            // given
            Long userId = 2L;
            Long roomId = 10L;
            User host = userWithId(1L, "방장");
            User requester = userWithId(userId, "Alice");
            User koreanMember = userWithId(3L, "가나다");
            User specialMember = userWithId(4L, "@runner");
            ImageFile requesterProfileImage = mock(ImageFile.class);
            given(requesterProfileImage.getUrl()).willReturn("https://example.com/profiles/2.png");
            given(requester.getImage()).willReturn(requesterProfileImage);
            Room room = roomWithDetails(
                roomId,
                "아침 운동 모임",
                "매주 함께 운동하고 인증하는 모임입니다.",
                List.of(ActivityDay.FRIDAY, ActivityDay.MONDAY, ActivityDay.WEDNESDAY),
                LocalTime.of(8, 0),
                "https://example.com/images/room-1.png",
                LocalDateTime.of(2026, 9, 20, 10, 30)
            );
            ReflectionTestUtils.setField(room, "hostUser", host);
            List<RoomUser> roomUsers = List.of(
                RoomUser.of(specialMember, room),
                RoomUser.of(requester, room),
                RoomUser.of(koreanMember, room),
                RoomUser.of(host, room)
            );
            given(userRepository.findById(userId)).willReturn(Optional.of(requester));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId))
                .willReturn(Optional.of(RoomUser.of(requester, room)));
            given(roomUserRepository.findActiveByRoomId(roomId)).willReturn(roomUsers);

            // when
            RoomDetailResponse response = roomService.getRoom(userId, roomId);

            // then
            assertThat(response.roomId()).isEqualTo(roomId);
            assertThat(response.name()).isEqualTo("아침 운동 모임");
            assertThat(response.description()).isEqualTo("매주 함께 운동하고 인증하는 모임입니다.");
            assertThat(response.imageUrl()).isEqualTo("https://example.com/images/room-1.png");
            assertThat(response.activityDays()).containsExactly("MONDAY", "WEDNESDAY", "FRIDAY");
            assertThat(response.activityTime()).isEqualTo("08:00");
            assertThat(response.memberCount()).isEqualTo(4);
            assertThat(response.membershipRole()).isEqualTo("MEMBER");
            assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 30));
            assertThat(response.members()).hasSize(4);
            assertThat(response.members()).extracting(RoomMemberResponse::nickname)
                .containsExactly("가나다", "방장", "Alice", "@runner");
            assertThat(response.members()).extracting(RoomMemberResponse::profileImageUrl)
                .containsExactly(null, null, "https://example.com/profiles/2.png", null);
            verify(roomUserRepository).findActiveByRoomId(roomId);
        }

        @Test
        void 방장은_OWNER_관계를_포함한_상세_정보를_조회한다() {
            // given
            Long hostUserId = 1L;
            Long roomId = 10L;
            User host = userWithId(hostUserId, "방장");
            Room room = roomWithHost(hostUserId, roomId);
            given(userRepository.findById(hostUserId)).willReturn(Optional.of(host));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(roomUserRepository.findActiveByRoomIdAndUserId(roomId, hostUserId))
                .willReturn(Optional.of(RoomUser.of(host, room)));
            given(roomUserRepository.findActiveByRoomId(roomId)).willReturn(List.of(RoomUser.of(host, room)));

            // when
            RoomDetailResponse response = roomService.getRoom(hostUserId, roomId);

            // then
            assertThat(response.membershipRole()).isEqualTo("OWNER");
            assertThat(response.memberCount()).isOne();
            assertThat(response.imageUrl()).isEqualTo("/images/default-room.png");
        }
    }

    @Nested
    class 모임방_상세_조회_접근_제어 {

        @Test
        void 인증되지_않은_사용자는_상세_정보를_조회할_수_없다() {
            // when
            Throwable thrown = catchThrowable(() -> roomService.getRoom(null, 10L));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_UNAUTHORIZED));
            verify(roomRepository, never()).findActiveById(any());
        }

        @Test
        void 존재하지_않거나_삭제된_모임방은_조회할_수_없다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.getRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NOT_FOUND));
            verify(roomUserRepository, never()).findActiveByRoomId(any());
        }

        @Test
        void 참여하지_않은_사용자는_상세_정보를_조회할_수_없다() {
            // given
            Long userId = 2L;
            Long roomId = 10L;
            User user = userWithId(userId, "외부인");
            Room room = roomWithHost(1L, roomId);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));
            given(roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> roomService.getRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DETAIL_FORBIDDEN));
            verify(roomUserRepository, never()).findActiveByRoomId(roomId);
        }

        @Test
        void 상세_정보_조회_중_예기치_않은_오류가_발생하면_ROOM500을_반환한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            User user = userWithId(userId, "가나다");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willThrow(new IllegalStateException("database error"));

            // when
            Throwable thrown = catchThrowable(() -> roomService.getRoom(userId, roomId));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DETAIL_FAILED));
        }
    }

    private RoomCreateRequest validRequest() {
        return new RoomCreateRequest(
            "아침 운동 모임",
            "매주 함께 운동하고 인증하는 모임입니다.",
            List.of(ActivityDay.MONDAY, ActivityDay.WEDNESDAY, ActivityDay.FRIDAY),
            LocalTime.of(8, 0)
        );
    }

    private User userWithId(Long userId, String nicknameValue) {
        User user = mock(User.class);
        Nickname nickname = mock(Nickname.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(user.getNickname()).thenReturn(nickname);
        lenient().when(nickname.getValue()).thenReturn(nicknameValue);
        return user;
    }

    private void givenValidUser() {
        User user = userWithId(1L, "가나다");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
    }

    private Room roomWithHost(Long hostUserId, Long roomId) {
        Room room = Room.of(
            userWithId(hostUserId, "방장"),
            null,
            "아침 운동 모임",
            "매주 함께 운동하고 인증하는 모임입니다.",
            List.of(ActivityDay.MONDAY),
            LocalTime.of(8, 0)
        );
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private Room roomWithDetails(
        Long roomId,
        String name,
        String description,
        List<ActivityDay> activityDays,
        LocalTime activityTime,
        String imageUrl,
        LocalDateTime createdAt
    ) {
        Room room = Room.of(userWithId(1L, "방장"), null, name, description, activityDays, activityTime);
        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(room, "created_at", createdAt);
        if (imageUrl != null) {
            ImageFile image = mock(ImageFile.class);
            given(image.getUrl()).willReturn(imageUrl);
            ReflectionTestUtils.setField(room, "image", image);
        }
        return room;
    }
}
