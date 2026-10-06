package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record RoomCreateResponse(
    @Schema(description = "생성된 모임 방 식별자", example = "1") Long roomId,
    @Schema(description = "모임 방 이름", example = "아침 운동 모임") String name,
    @Schema(description = "모임 소개. 값이 없으면 null", nullable = true) String description,
    @Schema(description = "대표 이미지 URL", example = "/images/default-room.png") String imageUrl,
    @Schema(description = "활동 요일 목록") List<String> activityDays,
    @Schema(description = "모든 활동 요일에 적용되는 시간 (HH:mm)", example = "08:00", pattern = "^\\d{2}:\\d{2}$") String activityTime,
    @Schema(description = "생성 직후 참여 중인 사용자 수", example = "1") Integer memberCount,
    @Schema(description = "생성 요청 사용자의 관계", allowableValues = {"OWNER"}) String membershipRole,
    @Schema(description = "방 최초 생성 일시 (ISO 8601)", format = "date-time") LocalDateTime createdAt,
    @Schema(description = "방장으로 자동 가입된 사용자") List<RoomMemberResponse> members
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomCreateResponse from(Room room, RoomUser roomUser, String imageUrl) {
        return new RoomCreateResponse(
            room.getId(),
            room.getName(),
            room.getDescription(),
            imageUrl,
            room.getActivityDays().stream().map(Enum::name).toList(),
            room.getActivityTime().format(TIME_FORMATTER),
            1,
            "OWNER",
            room.getCreated_at(),
            List.of(RoomMemberResponse.from(roomUser))
        );
    }
}
