package com.ringout.api.admin.repository;

import com.ringout.api.room.domain.Room;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class AdminRoomSearchRepositoryImpl implements AdminRoomSearchRepository {
    @PersistenceContext private EntityManager em;
    private static final String ACTIVE = """
        (r.latestActivityAt is not null and r.latestActivityAt >= :start and r.latestActivityAt < :end)
        """;

    @Override
    public Page<Room> searchFiltered(String search, String pattern, String sortBy, String direction,
            Boolean activeToday, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        String order = switch (sortBy) {
            case "id" -> "r.id";
            
            case "memberCount" -> "(select count(ru) from RoomUser ru where ru.room = r and ru.deletedAt is null and ru.user.deletedAt is null)";
            default -> throw new IllegalArgumentException("Unsupported sort field");
        };
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalArgumentException("Unsupported sort direction");
        }
        String where = " from Room r where r.deletedAt is null and (:search = '' or r.name like :pattern escape '!' or str(r.id) like :pattern escape '!' or r.hostUser.nickname.value like :pattern escape '!')"
                + (activeToday == null ? "" : " and " + (activeToday ? "" : "not ") + ACTIVE);
        var query = em.createQuery("select r" + where + " order by " + order + " " + direction
                + (sortBy.equals("id") ? "" : ", r.id desc"), Room.class);
        var graph = em.createEntityGraph(Room.class);
        graph.addAttributeNodes("image", "hostUser");
        query.setHint("jakarta.persistence.fetchgraph", graph);
        var count = em.createQuery("select count(r)" + where, Long.class);
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
        return em.createQuery("select r.id from Room r where r.id in :ids and r.deletedAt is null and " + ACTIVE, Long.class)
                .setParameter("ids", ids).setParameter("start", start).setParameter("end", end).getResultList();
    }
}
