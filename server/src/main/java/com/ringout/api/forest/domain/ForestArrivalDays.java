package com.ringout.api.forest.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ForestArrivalDays {

    private final List<LocalDate> days;

    private ForestArrivalDays(List<LocalDate> days) {
        this.days = days;
    }

    public static ForestArrivalDays from(List<LocalDateTime> arrivedAts) {
        List<LocalDate> days = arrivedAts.stream()
            .map(LocalDateTime::toLocalDate)
            .distinct()
            .sorted()
            .toList();
        return new ForestArrivalDays(days);
    }

    public int count() {
        return days.size();
    }

    public boolean canPlant(int growthCycle, LocalDate plantedDate) {
        int requiredDays = ForestTree.requiredArrivalDays(growthCycle);
        if (days.size() < requiredDays) {
            return false;
        }
        return days.get(requiredDays - 1).equals(plantedDate);
    }
}
