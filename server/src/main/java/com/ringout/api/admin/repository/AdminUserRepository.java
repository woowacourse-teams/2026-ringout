package com.ringout.api.admin.repository;

import com.ringout.api.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminUserRepository extends Repository<User, Long> {
    @EntityGraph(attributePaths = "image")
    @Query("select u from User u where u.deletedAt is null and (:search = '' or u.nickname.value like :pattern escape '!' or str(u.id) like :pattern escape '!')")
    Page<User> search(@Param("search") String search, @Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = "image")
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        select ru.user.id as userId, count(ru) as roomCount from RoomUser ru
        where ru.user.id in :ids and ru.deletedAt is null and ru.room.deletedAt is null
        group by ru.user.id
        """)
    List<RoomCount> countRooms(@Param("ids") List<Long> ids);

    @Query("select count(u) from User u where u.deletedAt is null")
    long countCurrentUsers();

    @Query("""
        select count(u) from User u where u.deletedAt is null
          and u.created_at >= :start and u.created_at < :end
        """)
    long countJoinedBetween(@Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("""
        select count(u) from User u where u.deletedAt is null and (
          (u.lastLoginAt >= :start and u.lastLoginAt < :end)
          or exists (
            select o.id from AlarmOccurrence o where o.user = u and o.deletedAt is null and (
              (o.startedAt >= :start and o.startedAt < :end)
              or (o.movementStartedAt >= :start and o.movementStartedAt < :end)
              or (o.endedAt >= :start and o.endedAt < :end)
              or exists (
                select r.id from AlarmRinging r where r.alarmOccurrence = o and r.deletedAt is null and (
                  (r.ringingAt >= :start and r.ringingAt < :end)
                  or (r.dismissedAt >= :start and r.dismissedAt < :end)
                )
              )
            )
          )
        )
        """)
    long countActiveBetween(@Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    interface RoomCount {
        Long getUserId();
        long getRoomCount();
    }
}
