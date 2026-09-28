package com.ringout.api.room.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "room_user",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_room_user_user_room",
        columnNames = {"user_id", "room_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    private RoomUser(User user, Room room) {
        this.user = user;
        this.room = room;
    }

    public static RoomUser of(User user, Room room) {
        return new RoomUser(user, room);
    }
}
