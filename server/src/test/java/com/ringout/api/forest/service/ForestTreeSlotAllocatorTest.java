package com.ringout.api.forest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ForestTreeSlotAllocatorTest {

    @Test
    void 비어_있는_칸만_배정한다() {
        // given
        ForestTreeSlotAllocator allocator = new ForestTreeSlotAllocator(new Random(0));
        Set<Integer> occupiedSlots = IntStream.range(0, 15).boxed().collect(Collectors.toSet());

        // when
        int slotIndex = allocator.allocate(occupiedSlots);

        // then
        assertThat(slotIndex).isEqualTo(15);
    }

    @Test
    void 비어_있는_모든_칸이_배정될_수_있다() {
        // given
        ForestTreeSlotAllocator allocator = new ForestTreeSlotAllocator(new Random(0));
        Set<Integer> occupiedSlots = Set.of(0, 5, 10);
        Set<Integer> allocatedSlots = new HashSet<>();

        // when
        for (int attempt = 0; attempt < 1_000; attempt++) {
            allocatedSlots.add(allocator.allocate(occupiedSlots));
        }

        // then
        assertThat(allocatedSlots)
            .hasSize(13)
            .doesNotContainAnyElementsOf(occupiedSlots)
            .allMatch(slotIndex -> slotIndex >= 0 && slotIndex < 16);
    }

    @Test
    void 빈_칸이_없으면_배정할_수_없다() {
        // given
        ForestTreeSlotAllocator allocator = new ForestTreeSlotAllocator(new Random(0));
        Set<Integer> occupiedSlots = IntStream.range(0, 16).boxed().collect(Collectors.toSet());

        // when // then
        assertThatThrownBy(() -> allocator.allocate(occupiedSlots))
            .isInstanceOf(IllegalStateException.class);
    }
}
