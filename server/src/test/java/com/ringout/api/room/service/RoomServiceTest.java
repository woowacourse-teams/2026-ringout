package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.repository.RoomBlackListRepository;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.Nickname;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

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
        void 모임방을_soft_delete하고_블랙리스트를_제거한다() {
            // given
            Long userId = 1L;
            Long roomId = 10L;
            User user = userWithId(userId, "가나다");
            Room room = roomWithHost(userId, roomId);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(roomRepository.findActiveById(roomId)).willReturn(Optional.of(room));

            // when
            roomService.deleteRoom(userId, roomId);

            // then
            assertThat(room.getDeletedAt()).isNotNull();
            verify(roomBlackListRepository).deleteAllByRoom(room);
            verify(roomRepository, never()).delete(room);
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
            verify(roomBlackListRepository, never()).deleteAllByRoom(any());
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
            verify(roomBlackListRepository, never()).deleteAllByRoom(any());
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
            verify(roomBlackListRepository, never()).deleteAllByRoom(any());
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
            verify(roomBlackListRepository, never()).deleteAllByRoom(any());
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
                    assertThat(exception.getCode()).isEqualTo(com.ringout.api.user.status.UserErrorStatus.USER_NOT_FOUND));
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
}
