package com.ringout.api.forest.repository;

import com.ringout.api.forest.domain.ForestTree;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
        select tree.slotIndex
        from ForestTree tree
        where tree.user.id = :userId
        """)
    List<Integer> findSlotIndexesByUserId(@Param("userId") Long userId);
}
