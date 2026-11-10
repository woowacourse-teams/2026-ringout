package com.joon.ringout.domain.forest

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForestGrowthTest {
    @Test
    fun `성공한 날짜 다섯 개마다 나무를 심고 다음 씨앗을 바로 시작한다`() {
        val cases = listOf(
            Triple(0, 0, 0), Triple(4, 0, 4), Triple(5, 1, 0),
            Triple(6, 1, 1), Triple(79, 15, 4),
        )
        for ((days, trees, filled) in cases) {
            val growth = ForestGrowth(days)
            assertEquals(days, growth.growthDayCount)
            assertEquals(trees, growth.completedTreeCount)
            assertEquals(filled, growth.filledSlots)
            assertEquals(5 - filled, growth.remainingDays)
            assertTrue(growth.hasGrowingSeed)
            assertFalse(growth.isComplete)
        }
    }

    @Test
    fun `여든째 날 숲을 완성한 뒤에도 도착 횟수는 보존하고 다음 씨앗은 만들지 않는다`() {
        for (days in listOf(80, 81, 100, Int.MAX_VALUE)) {
            val growth = ForestGrowth(days)
            assertEquals(days, growth.successfulDayCount)
            assertEquals(80, growth.growthDayCount)
            assertEquals(16, growth.completedTreeCount)
            assertEquals(0, growth.filledSlots)
            assertEquals(0, growth.remainingDays)
            assertFalse(growth.hasGrowingSeed)
            assertTrue(growth.isComplete)
        }
    }

    @Test
    fun `세 종류는 각각 선택 가능하고 유일하게 남은 빈 칸에 심는다`() {
        for ((index, kind) in ForestTreeKind.entries.withIndex()) {
            val random = object : Random() {
                private var calls = 0
                override fun nextBits(bitCount: Int): Int = error("Unexpected random operation")
                override fun nextInt(until: Int): Int = when (calls++) {
                    0 -> { assertEquals(3, until); index }
                    else -> { assertEquals(1, until); 0 }
                }
            }
            val tree = ForestPlantingPolicy.createTree(16, (0..15).toSet() - 7, 8_000, random)
            assertEquals(kind, tree.kind)
            assertEquals(7, tree.slotIndex)
            assertEquals(1, tree.row)
            assertEquals(3, tree.column)
            assertEquals(8_000L, tree.plantedAtEpochMillis)
        }
        assertEquals(setOf("pine", "oak", "big_tree"), ForestTreeKind.entries.map { it.persistedValue }.toSet())
    }

    @Test
    fun `나무 열여섯 그루의 위치는 겹치지 않고 가득 찬 숲에는 추가하지 않는다`() {
        val slots = mutableSetOf<Int>()
        val random = Random(651)
        repeat(16) { index ->
            val tree = ForestPlantingPolicy.createTree(index + 1, slots, index.toLong(), random)
            assertTrue(slots.add(tree.slotIndex))
        }
        assertEquals((0..15).toSet(), slots)
        assertFailsWith<IllegalArgumentException> { ForestPlantingPolicy.createTree(16, slots, 0) }
        assertFailsWith<IllegalArgumentException> { ForestPlantingPolicy.createTree(17, emptySet(), 0) }
    }

    @Test
    fun `미확인 안내만 성장 회차 순서로 반환한다`() {
        val trees = listOf(
            ForestTree(3, ForestTreeKind.BigTree, 12, 300),
            ForestTree(2, ForestTreeKind.Oak, 6, 200, 400),
            ForestTree(1, ForestTreeKind.Pine, 0, 100),
        )
        val state = ForestState(ForestGrowth(15), trees, null)
        assertEquals(listOf(1, 3), state.pendingTrees.map { it.growthCycle })
        assertFalse(state.isOnboardingCompleted)
        assertTrue(state.copy(onboardingCompletedAtEpochMillis = 500).isOnboardingCompleted)
    }

    @Test
    fun `음수 성장과 범위를 벗어난 나무 위치는 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> { ForestGrowth(-1) }
        assertFailsWith<IllegalArgumentException> { ForestTree(0, ForestTreeKind.Pine, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ForestTree(1, ForestTreeKind.Pine, 16, 0) }
        assertFailsWith<IllegalArgumentException> { ForestPlantingPolicy.createTree(1, setOf(-1), 0) }
        assertFailsWith<IllegalStateException> { ForestTreeKind.fromPersistedValue("sunleaf") }
    }
}
