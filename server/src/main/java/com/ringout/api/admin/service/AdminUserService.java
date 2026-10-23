package com.ringout.api.admin.service;

import com.ringout.api.admin.dto.response.AdminUserResponses.*;
import com.ringout.api.admin.repository.AdminUserRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.terms.service.TermsService;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.status.UserErrorStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {
    private final AdminUserRepository users;
    private final RoomUserRepository roomUsers;
    private final TermsService terms;
    private final ImageFileService images;

    public UserPage getUsers(String search, int page, int size) {
        String keyword = search == null ? "" : search.trim();
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = users.search(keyword, pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Long> counts = roomCounts(result.getContent().stream().map(User::getId).toList());
        var content = result.getContent().stream().map(user -> new UserSummary(
                user.getId(), user.getNickname().getValue(), imageUrl(user.getImage()),
                user.getSocialProvider(), user.getCreated_at(), counts.getOrDefault(user.getId(), 0L)))
                .toList();
        return new UserPage(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    public UserDetail getUser(Long userId) {
        User user = requireUser(userId);
        return new UserDetail(user.getId(), user.getNickname().getValue(), imageUrl(user.getImage()),
                user.getSocialProvider(), maskEmail(user.getEmail()), user.getCreated_at(),
                user.getLastLoginAt(), user.getRole(), roomCounts(List.of(userId)).getOrDefault(userId, 0L),
                terms.getTermsAgreements(userId));
    }

    public List<JoinedRoom> getRooms(Long userId) {
        requireUser(userId);
        var rooms = roomUsers.findActiveRoomsByUserId(userId);
        if (rooms.isEmpty()) return List.of();
        var stats = roomUsers.findMemberStatsbyRoomIds(rooms.stream().map(room -> room.getId()).toList(), userId)
                .stream().collect(Collectors.toMap(stat -> stat.roomId(), Function.identity()));
        return rooms.stream().sorted(java.util.Comparator.comparing(room -> room.getId()))
                .map(room -> new JoinedRoom(room.getId(), room.getName(), imageUrl(room.getImage()),
                        room.getActivityDays(), room.getActivityTime(),
                        stats.containsKey(room.getId()) ? stats.get(room.getId()).memberCount() : 0L,
                        room.isHostedBy(userId))).toList();
    }

    private User requireUser(Long id) {
        return users.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));
    }

    private Map<Long, Long> roomCounts(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.countRooms(ids).stream().collect(Collectors.toMap(
                AdminUserRepository.RoomCount::getUserId, AdminUserRepository.RoomCount::getRoomCount));
    }

    private String imageUrl(ImageFile image) {
        return image == null ? null : images.createReadUri(image).toString();
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at < 1) return "***";
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
