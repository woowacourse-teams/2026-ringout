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

    interface RoomCount {
        Long getUserId();
        long getRoomCount();
    }
}
