package com.ringout.api.room.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "room"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    private static final String ROOM_NAME_PATTERN = "^[가-힣A-Za-z0-9 ]+$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id", nullable = false)
    @Getter
    private User hostUser;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", unique = true)
    @Getter
    private ImageFile image;

    @Column(nullable = false, length = 20)
    @Getter
    private String name;

    @Column(length = 300)
    @Getter
    private String description;

    @Column(name = "activity_days", nullable = false)
    @Convert(converter = ActivityDaysConverter.class)
    private ActivityDays activityDays;

    @Column(name = "activity_time", nullable = false)
    @Getter
    private LocalTime activityTime;

    @Column(name = "latest_activity_at", nullable = false)
    @Getter
    private LocalDateTime latestActivityAt;

    private Room(User hostUser, ImageFile image, String name, String description, List<ActivityDay> activityDays,
        LocalTime activityTime) {
        this.hostUser = hostUser;
        this.image = image;
        this.name = name;
        this.description = description;
        this.activityDays = ActivityDays.from(activityDays);
        this.activityTime = activityTime;
        this.latestActivityAt = LocalDateTime.now();
    }

    public static Room of(User hostUser, ImageFile image, String rawName, String description, List<ActivityDay> activityDays,
        LocalTime activityTime) {
        String name = validateName(rawName);
        validateDescription(description);
        validateActivityTime(activityTime);

        return new Room(hostUser, image, name, description, activityDays, activityTime);
    }

    public void addActivityDay(ActivityDay activityDay) {
        this.activityDays = this.activityDays.add(activityDay);
    }

    public void removeActivityDay(ActivityDay activityDay) {
        this.activityDays = this.activityDays.remove(activityDay);
    }

    public void changeActivityDays(List<ActivityDay> activityDays) {
        this.activityDays = ActivityDays.from(activityDays);
    }

    public void update(String rawName, String description) {
        if (rawName == null && description == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_UPDATE_REQUIRED);
        }

        if (rawName != null) {
            this.name = validateName(rawName);
        }

        if (description != null) {
            validateDescriptionForUpdate(description);
            this.description = description;
        }
    }

    public void recordActivityAt(LocalDateTime activityAt) {
        if (activityAt.isAfter(latestActivityAt)) {
            latestActivityAt = activityAt;
        }
    }

    public boolean isHostedBy(Long userId) {
        return hostUser != null && hostUser.getId() != null && hostUser.getId().equals(userId);
    }

    public void softDelete() {
        markDeleted();
    }

    public List<ActivityDay> getActivityDays() {
        return activityDays.getDays();
    }

    private static String validateName(String name) {
        if (name == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_NAME_INVALID);
        }

        String trimmedName = name.trim();
        if (trimmedName.length() < 2
            || trimmedName.length() > 20
            || !trimmedName.matches(ROOM_NAME_PATTERN)) {
            throw new GeneralException(RoomErrorStatus.ROOM_NAME_INVALID);
        }

        return trimmedName;
    }

    private static void validateDescription(String description) {
        if (description != null && (description.isBlank() || description.length() > 300)) {
            throw new GeneralException(RoomErrorStatus.ROOM_DESCRIPTION_INVALID);
        }
    }

    private static void validateDescriptionForUpdate(String description) {
        if (description.length() > 300) {
            throw new GeneralException(RoomErrorStatus.ROOM_DESCRIPTION_INVALID);
        }
    }

    private static void validateActivityTime(LocalTime activityTime) {
        if (activityTime == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_ACTIVITY_TIME_INVALID);
        }
    }
}
