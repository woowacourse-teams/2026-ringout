package com.ringout.api.forest.repository;

import com.ringout.api.forest.domain.ForestTree;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForestTreeRepository extends JpaRepository<ForestTree, Long> {

    @Query("""
        select tree
        from ForestTree tree
        where tree.user.id = :userId
          and tree.growthCycle = :growthCycle
        """)
    Optional<ForestTree> findByUserIdAndGrowthCycle(
        @Param("userId") Long userId,
        @Param("growthCycle") int growthCycle
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select tree
        from ForestTree tree
        where tree.user.id = :userId
          and tree.growthCycle = :growthCycle
        """)
    Optional<ForestTree> findByUserIdAndGrowthCycleForUpdate(
        @Param("userId") Long userId,
        @Param("growthCycle") int growthCycle
    );

    @Query("""
        select tree
        from ForestTree tree
        where tree.user.id = :userId
        order by tree.growthCycle asc
        """)
    List<ForestTree> findAllByUserIdOrderByGrowthCycle(@Param("userId") Long userId);

    @Query("""
        select tree.slotIndex
        from ForestTree tree
        where tree.user.id = :userId
        """)
    List<Integer> findSlotIndexesByUserId(@Param("userId") Long userId);
}
