package com.ringout.api.admin.repository;

import com.ringout.api.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserSearchRepository {
    Page<User> searchFiltered(String search, String pattern, String sortBy, String direction,
            Boolean activeToday, LocalDateTime start, LocalDateTime end, Pageable pageable);
    List<Long> findActiveIds(List<Long> ids, LocalDateTime start, LocalDateTime end);
}
