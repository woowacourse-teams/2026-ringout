package com.ringout.api.admin.dto.response;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.terms.dto.response.TermsAgreementsResponse;
import com.ringout.api.user.domain.Role;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AdminUserResponses {
    private AdminUserResponses() {}

    public record UserStats(java.time.LocalDate date, String timeZone, LocalDateTime updatedAt,
            long totalUsers, long joinedToday, long activeToday) {}

    public record UserSummary(Long id, String nickname, String imageUrl,
            SocialProvider provider, LocalDateTime joinedAt, long roomCount) {}

    public record UserPage(List<UserSummary> content, int page, int size,
            long totalElements, int totalPages) {}

    public record UserDetail(Long id, String nickname, String imageUrl,
            SocialProvider provider, String maskedEmail, LocalDateTime joinedAt,
            LocalDateTime lastLoginAt, Role role, long roomCount,
            TermsAgreementsResponse termsAgreements) {}

    public record JoinedRoom(Long id, String name, String imageUrl,
            List<ActivityDay> activityDays, LocalTime activityTime,
            long memberCount, boolean host) {}
}
