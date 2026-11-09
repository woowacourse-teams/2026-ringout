package com.joon.ringout.domain.forest

import kotlin.random.Random

object ForestPlantingPolicy {
    /** Call only for a missing growth cycle, then persist the result in the success transaction. */
    fun createTree(
        growthCycle: Int,
        occupiedSlots: Set<Int>,
        plantedAtEpochMillis: Long,
        random: Random = Random.Default,
    ): ForestTree {
        require(growthCycle in 1..ForestGrowth.MaxTrees)
        require(occupiedSlots.all { it in 0 until ForestGrowth.MaxTrees })
        val availableSlots = (0 until ForestGrowth.MaxTrees).filterNot(occupiedSlots::contains)
        require(availableSlots.isNotEmpty()) { "The forest is full." }
        return ForestTree(
            growthCycle = growthCycle,
            kind = ForestTreeKind.entries[random.nextInt(ForestTreeKind.entries.size)],
            slotIndex = availableSlots[random.nextInt(availableSlots.size)],
            plantedAtEpochMillis = plantedAtEpochMillis,
        )
    }
}
