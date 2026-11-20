package com.ringout.api.forest.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "forest_tree",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_forest_tree_user_growth_cycle", columnNames = {"user_id", "growth_cycle"}),
        @UniqueConstraint(name = "uk_forest_tree_user_slot_index", columnNames = {"user_id", "slot_index"})
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForestTree extends BaseEntity {

    public static final int MIN_GROWTH_CYCLE = 1;
    public static final int MAX_GROWTH_CYCLE = 16;
    public static final int SLOT_COUNT = 16;
    public static final int DAYS_PER_TREE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "growth_cycle", nullable = false)
    private int growthCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private TreeKind kind;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "planted_at", nullable = false)
    private LocalDateTime plantedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    private ForestTree(User user, int growthCycle, TreeKind kind, int slotIndex, LocalDateTime plantedAt) {
        this.user = user;
        this.growthCycle = growthCycle;
        this.kind = kind;
        this.slotIndex = slotIndex;
        this.plantedAt = plantedAt;
    }

    public static ForestTree plant(User user, int growthCycle, TreeKind kind, int slotIndex, LocalDateTime plantedAt) {
        if (!isValidGrowthCycle(growthCycle)) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID);
        }
        if (kind == null || plantedAt == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID);
        }
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            throw new IllegalArgumentException("숲 칸 번호는 0 이상 15 이하여야 합니다. slotIndex=" + slotIndex);
        }

        return new ForestTree(user, growthCycle, kind, slotIndex, plantedAt.truncatedTo(ChronoUnit.SECONDS));
    }

    public static int parseGrowthCycle(String rawGrowthCycle) {
        int growthCycle = parseInteger(rawGrowthCycle);
        if (!isValidGrowthCycle(growthCycle)) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID);
        }
        return growthCycle;
    }

    public static int requiredArrivalDays(int growthCycle) {
        return growthCycle * DAYS_PER_TREE;
    }

    public boolean acknowledge(LocalDateTime acknowledgedAt) {
        if (acknowledgedAt == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID);
        }
        if (this.acknowledgedAt != null) {
            return false;
        }
        this.acknowledgedAt = acknowledgedAt.truncatedTo(ChronoUnit.SECONDS);
        return true;
    }

    private static int parseInteger(String rawGrowthCycle) {
        if (rawGrowthCycle == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID);
        }
        try {
            return Integer.parseInt(rawGrowthCycle);
        } catch (NumberFormatException exception) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID);
        }
    }

    private static boolean isValidGrowthCycle(int growthCycle) {
        return growthCycle >= MIN_GROWTH_CYCLE && growthCycle <= MAX_GROWTH_CYCLE;
    }
}
