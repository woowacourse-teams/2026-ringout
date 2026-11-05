package com.ringout.api.admin.repository;

import com.ringout.api.admin.dto.response.AdminAlarmHistoryResponses.Status;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAlarmHistoryRepository {
    @PersistenceContext private EntityManager em;

    public Page<AlarmOccurrence> search(LocalDateTime start, LocalDateTime end, Long userId,
            Status status, LocalDateTime referenceTime, String direction, Pageable pageable) {
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new IllegalArgumentException("Unsupported sort direction");
        }
        String filter = status == null ? "" : switch (status) {
            case ARRIVED, FORCE_ENDED -> " and o.endType = :endType";
            case ALARM_TRIGGERED -> " and o.endType is null and o.movementStartedAt is null";
            case MOVEMENT_STARTED -> " and o.endType is null and o.movementStartedAt > :cutoff";
            case MOVING -> " and o.endType is null and o.movementStartedAt <= :cutoff";
        };
        String where = " from AlarmOccurrence o where o.deletedAt is null and o.user.deletedAt is null"
                + " and o.startedAt >= :start and o.startedAt < :end"
                + (userId == null ? "" : " and o.user.id = :userId") + filter;
        var query = em.createQuery("select o" + where + " order by o.startedAt " + direction
                + ", o.id " + direction, AlarmOccurrence.class);
        var graph = em.createEntityGraph(AlarmOccurrence.class);
        graph.addAttributeNodes("user");
        query.setHint("jakarta.persistence.fetchgraph", graph);
        var count = em.createQuery("select count(o)" + where, Long.class);
        bind(query, start, end, userId, status, referenceTime);
        bind(count, start, end, userId, status, referenceTime);
        return new PageImpl<>(query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList(), pageable, count.getSingleResult());
    }

    private void bind(TypedQuery<?> query, LocalDateTime start, LocalDateTime end, Long userId,
            Status status, LocalDateTime referenceTime) {
        query.setParameter("start", start).setParameter("end", end);
        if (userId != null) query.setParameter("userId", userId);
        if (status == Status.ARRIVED || status == Status.FORCE_ENDED) {
            query.setParameter("endType", OccurrenceEndType.valueOf(status.name()));
        } else if (status == Status.MOVING || status == Status.MOVEMENT_STARTED) {
            query.setParameter("cutoff", referenceTime.minusMinutes(2));
        }
    }
}
