package com.ringout.api.room.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomActivityServiceTest {

    @Mock
    private RoomUserRepository roomUserRepository;

    @Test
    void 현재_참여한_모든_방에_회원의_알람_기록_활동_시각을_반영한다() {
        // given
        Room firstRoom = Room.of(mockUser(), null, "아침 모임", null, List.of(ActivityDay.MONDAY), LocalTime.of(8, 0));
        Room secondRoom = Room.of(mockUser(), null, "저녁 모임", null, List.of(ActivityDay.TUESDAY), LocalTime.of(19, 0));
        LocalDateTime occurredAt = LocalDateTime.of(2099, 9, 30, 10, 30);
        RoomActivityService service = new RoomActivityService(roomUserRepository);
        given(roomUserRepository.findActiveRoomsByUserId(1L)).willReturn(List.of(firstRoom, secondRoom));

        // when
        service.recordMemberAlarmActivity(1L, occurredAt);

        // then
        verify(roomUserRepository).findActiveRoomsByUserId(1L);
        org.assertj.core.api.Assertions.assertThat(firstRoom.getLatestActivityAt()).isEqualTo(occurredAt);
        org.assertj.core.api.Assertions.assertThat(secondRoom.getLatestActivityAt()).isEqualTo(occurredAt);
    }

    @Test
    void 이동_상태가_바뀐_방에만_활동_시각을_반영한다() {
        // given
        Room room = Room.of(mockUser(), null, "아침 모임", null, List.of(ActivityDay.MONDAY), LocalTime.of(8, 0));
        LocalDateTime occurredAt = LocalDateTime.of(2099, 9, 30, 10, 30);
        RoomActivityService service = new RoomActivityService(roomUserRepository);

        // when
        service.recordMovementActivity(room, occurredAt);

        // then
        org.assertj.core.api.Assertions.assertThat(room.getLatestActivityAt()).isEqualTo(occurredAt);
    }

    private User mockUser() {
        return org.mockito.Mockito.mock(User.class);
    }
}
