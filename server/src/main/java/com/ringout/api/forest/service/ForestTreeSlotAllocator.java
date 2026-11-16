package com.ringout.api.forest.service;

import com.ringout.api.forest.domain.ForestTree;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class ForestTreeSlotAllocator {

    private final RandomGenerator random;

    public ForestTreeSlotAllocator() {
        this(new Random());
    }

    ForestTreeSlotAllocator(RandomGenerator random) {
        this.random = random;
    }

    public int allocate(Set<Integer> occupiedSlots) {
        List<Integer> emptySlots = IntStream.range(0, ForestTree.SLOT_COUNT)
            .boxed()
            .filter(slotIndex -> !occupiedSlots.contains(slotIndex))
            .toList();
        if (emptySlots.isEmpty()) {
            throw new IllegalStateException("숲에 빈 칸이 없습니다.");
        }
        return emptySlots.get(random.nextInt(emptySlots.size()));
    }
}
