package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RoomDetailResponse(
    @Schema(description = "모임 방 식별자", example = "1") Long roomId,
    @Schema(description = "모임 방 이름", example = "아침 운동 모임") String name,
    @Schema(description = "모임 소개. 값이 없으면 null", nullable = true) String description,
    @Schema(description = "대표 이미지 URL. 이미지가 없으면 기본 이미지 URL", example = "/images/default-room.png") String imageUrl,
    @Schema(description = "활동 요일. 월요일부터 일요일 순", example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]") List<String> activityDays,
    @Schema(description = "모든 활동 요일에 적용되는 시간 (HH:mm)", example = "08:00", pattern = "^\\d{2}:\\d{2}$") String activityTime,
    @Schema(description = "현재 참여 중인 사용자 수이며 members 크기와 동일", example = "4") Integer memberCount,
    @Schema(description = "현재 인증 사용자와의 관계", allowableValues = {"OWNER", "MEMBER"}) String membershipRole,
    @Schema(description = "방 최초 생성 일시 (ISO 8601)", example = "2026-09-20T10:30:00", format = "date-time") LocalDateTime createdAt,
    @Schema(description = "방장과 현재 참여 중인 회원 목록. 닉네임 정렬") List<RoomMemberResponse> members
) {

    private static final String DEFAULT_ROOM_IMAGE_URL = "/images/default-room.png";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomDetailResponse from(Room room, Long currentUserId, List<RoomUser> roomUsers) {
        List<RoomMemberResponse> members = roomUsers.stream()
            .map(RoomMemberResponse::from)
            .sorted(Comparator.comparing(RoomMemberResponse::nickname, RoomDetailResponse::compareNickname))
            .collect(LinkedHashMap<Long, RoomMemberResponse>::new,
                (uniqueMembers, member) -> uniqueMembers.putIfAbsent(member.userId(), member),
                Map::putAll)
            .values()
            .stream()
            .toList();

        String imageUrl = room.getImage() == null ? DEFAULT_ROOM_IMAGE_URL : room.getImage().getUrl();
        String membershipRole = room.isHostedBy(currentUserId) ? "OWNER" : "MEMBER";

        return new RoomDetailResponse(
            room.getId(),
            room.getName(),
            room.getDescription(),
            imageUrl,
            room.getActivityDays().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .toList(),
            room.getActivityTime().format(TIME_FORMATTER),
            members.size(),
            membershipRole,
            room.getCreated_at(),
            members
        );
    }

    private static int compareNickname(String left, String right) {
        int groupComparison = Integer.compare(nicknameGroup(left), nicknameGroup(right));
        return groupComparison != 0 ? groupComparison : left.compareTo(right);
    }

    private static int nicknameGroup(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return 2;
        }

        char firstCharacter = nickname.charAt(0);
        if (firstCharacter >= '\uAC00' && firstCharacter <= '\uD7A3') {
            return 0;
        }
        if ((firstCharacter >= 'A' && firstCharacter <= 'Z')
            || (firstCharacter >= 'a' && firstCharacter <= 'z')) {
            return 1;
        }
        return 2;
    }
}
