package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RoomDetailResponse(
    Long roomId,
    String name,
    String description,
    String imageUrl,
    List<String> activityDays,
    String activityTime,
    Integer memberCount,
    String membershipRole,
    LocalDateTime createdAt,
    List<RoomMemberResponse> members
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
