package com.ringout.api.admin.repository;

import com.ringout.api.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class AdminUserSearchRepositoryImpl implements AdminUserSearchRepository {
    @PersistenceContext private EntityManager em;
    private static final String ACTIVE = """
        ((u.lastLoginAt is not null and u.lastLoginAt >= :start and u.lastLoginAt < :end)
          or exists (
            select o.id from AlarmOccurrence o where o.user = u and o.deletedAt is null and (
              (o.startedAt >= :start and o.startedAt < :end)
              or (o.movementStartedAt >= :start and o.movementStartedAt < :end)
              or (o.endedAt >= :start and o.endedAt < :end)
              or exists (
                select a.id from AlarmRinging a where a.alarmOccurrence = o and a.deletedAt is null
                  and ((a.ringingAt >= :start and a.ringingAt < :end)
                    or (a.dismissedAt >= :start and a.dismissedAt < :end))
              )
            )
          ))
        """;

    @Override
    public Page<User> searchFiltered(String search, String pattern, String sortBy, String direction,
            Boolean activeToday, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        String order = switch (sortBy) {
            case "id" -> "u.id";
            case "joinedAt" -> "u.created_at";
            case "roomCount" -> "(select count(ru) from RoomUser ru where ru.user = u and ru.deletedAt is null and ru.room.deletedAt is null)";
            default -> throw new IllegalArgumentException("Unsupported sort field");
        };
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalArgumentException("Unsupported sort direction");
        }
        String where = " from User u where u.deletedAt is null and (:search = '' or u.nickname.value like :pattern escape '!' or str(u.id) like :pattern escape '!')"
                + (activeToday == null ? "" : " and " + (activeToday ? "" : "not ") + ACTIVE);
        var query = em.createQuery("select u" + where + " order by " + order + " " + direction
                + (sortBy.equals("id") ? "" : ", u.id desc"), User.class);
        var graph = em.createEntityGraph(User.class);
        graph.addAttributeNodes("image");
        query.setHint("jakarta.persistence.fetchgraph", graph);
        var count = em.createQuery("select count(u)" + where, Long.class);
        bind(query, search, pattern, activeToday, start, end);
        bind(count, search, pattern, activeToday, start, end);
        return new PageImpl<>(query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList(), pageable, count.getSingleResult());
    }

    private void bind(TypedQuery<?> query, String search, String pattern, Boolean activeToday,
            LocalDateTime start, LocalDateTime end) {
        query.setParameter("search", search).setParameter("pattern", pattern);
        if (activeToday != null) query.setParameter("start", start).setParameter("end", end);
    }

    @Override
    public List<Long> findActiveIds(List<Long> ids, LocalDateTime start, LocalDateTime end) {
        if (ids.isEmpty()) return List.of();
        return em.createQuery("select u.id from User u where u.id in :ids and u.deletedAt is null and " + ACTIVE, Long.class)
                .setParameter("ids", ids).setParameter("start", start).setParameter("end", end).getResultList();
    }
}
