package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.Nickname;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUserRepository roomUserRepository;

    @Mock
    private UserRepository userRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomUserRepository, userRepository);
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
}
