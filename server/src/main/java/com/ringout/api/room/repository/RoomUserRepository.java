package com.ringout.api.room.repository;

import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.domain.Room;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomUserRepository extends JpaRepository<RoomUser, Long> {

    @Query("""
        select roomUser
        from RoomUser roomUser
        where roomUser.room.id = :roomId
          and roomUser.user.id = :userId
          and roomUser.deletedAt is null
        """)
    Optional<RoomUser> findActiveByRoomIdAndUserId(@Param("roomId") Long roomId,
        @Param("userId") Long userId);

    Optional<RoomUser> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("""
        select roomUser
        from RoomUser roomUser
        join fetch roomUser.user user
        left join fetch user.image
        where roomUser.room.id = :roomId
          and roomUser.deletedAt is null
        """)
    List<RoomUser> findActiveByRoomId(@Param("roomId") Long roomId);

    @Query("""
        select count(roomUser)
        from RoomUser roomUser
        where roomUser.room.id = :roomId
          and roomUser.deletedAt is null
        """)
    int countActiveByRoomId(@Param("roomId") Long roomId);

    @Query("""
        select case when count(roomUser) > 0 then true else false end
        from RoomUser roomUser
        where roomUser.room.id = :roomId
          and roomUser.user.id = :userId
          and roomUser.deletedAt is null
        """)
    boolean existsActiveByRoomIdAndUserId(@Param("roomId") Long roomId,
        @Param("userId") Long userId);

    @Query("""
        select roomUser.room
        from RoomUser roomUser
        where roomUser.user.id = :userId
          and roomUser.deletedAt is null
          and roomUser.room.deletedAt is null
        """)
    List<Room> findActiveRoomsByUserId(@Param("userId") Long userId);

    @Query("""                                                                                                                                                                      
      select new com.ringout.api.room.repository.RoomMemberStat(                                                                                                                  
          roomUser.room.id,                                                                                                                                                       
          count(roomUser),                                                                                                                                                        
          sum(case when roomUser.user.id = :userId then 1 else 0 end)                                                                                                             
      )                                                                                                                                                                           
      from RoomUser roomUser                                                                                                                                                      
      where roomUser.room.id in :roomIds                                                                                                                                          
        and roomUser.deletedAt is null                                                                                                                                            
      group by roomUser.room.id                                                                                                                                                   
      """)
    List<RoomMemberStat> findMemberStatsbyRoomIds(@Param("roomIds") List<Long> roomIds,
        @Param("userId") Long userId);
}
