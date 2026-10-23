package com.ringout.api.admin.repository;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminRoomRepository extends Repository<Room, Long> {
    @EntityGraph(attributePaths = {"image", "hostUser"})
    @Query("""
        select r from Room r where r.deletedAt is null and
        (:search = '' or r.name like :pattern escape '!'
          or str(r.id) like :pattern escape '!'
          or r.hostUser.nickname.value like :pattern escape '!')
        """)
    Page<Room> search(@Param("search") String search, @Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = {"image", "hostUser"})
    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        select ru.room.id as roomId, count(ru) as memberCount from RoomUser ru
        where ru.room.id in :roomIds and ru.deletedAt is null and ru.user.deletedAt is null
        group by ru.room.id
        """)
    List<MemberCount> countMembers(@Param("roomIds") List<Long> roomIds);

    @Query("""
        select ru from RoomUser ru join fetch ru.user u left join fetch u.image
        where ru.room.id = :roomId and ru.deletedAt is null and u.deletedAt is null
        order by u.id asc
        """)
    List<RoomUser> findMembers(@Param("roomId") Long roomId);

    @Query("select count(r) from Room r where r.deletedAt is null")
    long countCurrentRooms();

    @Query("""
        select count(r) from Room r where r.deletedAt is null
          and r.created_at >= :start and r.created_at < :end
        """)
    long countCreatedBetween(@Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("""
        select count(r) from Room r where r.deletedAt is null
          and r.latestActivityAt >= :start and r.latestActivityAt < :end
        """)
    long countActiveBetween(@Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    interface MemberCount {
        Long getRoomId();
        long getMemberCount();
    }
}
