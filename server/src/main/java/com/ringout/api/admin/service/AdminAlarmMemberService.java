package com.ringout.api.admin.service;

import com.ringout.api.admin.repository.AdminUserRepository;
import com.ringout.api.user.domain.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAlarmMemberService {
    private final AdminUserRepository users;
    private final Clock clock;

    public MemberPage getMembers(LocalDate date, String search, int page, int size) {
        var day = date == null ? LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))) : date;
        var start = day.atStartOfDay();
        var end = start.plusDays(1);
        var keyword = search == null ? "" : search.trim();
        var pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = users.searchFiltered(keyword, pattern, "id", "desc", null, start, end, PageRequest.of(page, size));
        var active = new HashSet<>(users.findIdsWithAlarmRecords(result.getContent().stream().map(User::getId).toList(), start, end));
        return new MemberPage(day, result.getContent().stream().map(user -> new Member(
                user.getId(), user.getNickname().getValue(), active.contains(user.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public record Member(Long id, String nickname, boolean activeOnDate) {}
    public record MemberPage(LocalDate date, List<Member> content, int page, int size, long totalElements, int totalPages) {}
}
