package com.ringout.api.forest.domain;

import lombok.Getter;

@Getter
public class ForestGrowth {

    private static final int MAX_GROWTH_DAYS = ForestTree.requiredArrivalDays(ForestTree.MAX_GROWTH_CYCLE);

    private final int successfulDayCount;

    private ForestGrowth(int successfulDayCount) {
        this.successfulDayCount = successfulDayCount;
    }

    public static ForestGrowth of(int successfulDayCount) {
        if (successfulDayCount < 0) {
            throw new IllegalArgumentException("성공 날짜 수는 음수일 수 없습니다. successfulDayCount=" + successfulDayCount);
        }
        return new ForestGrowth(successfulDayCount);
    }

    public int getFilledSlots() {
        if (isComplete()) {
            return 0;
        }
        return growthDayCount() % ForestTree.DAYS_PER_TREE;
    }

    public int getRemainingDays() {
        if (isComplete()) {
            return 0;
        }
        return ForestTree.DAYS_PER_TREE - getFilledSlots();
    }

    public boolean isComplete() {
        return growthDayCount() / ForestTree.DAYS_PER_TREE == ForestTree.MAX_GROWTH_CYCLE;
    }

    private int growthDayCount() {
        return Math.min(successfulDayCount, MAX_GROWTH_DAYS);
    }
}
