package com.joon.ringout.domain.forest

import kotlin.random.Random

object ForestPlantingPolicy {
    fun growthCycleToPlant(successfulDayCount: Int): Int? {
        require(successfulDayCount >= 0)
        return successfulDayCount.takeIf {
            it in ForestGrowth.DaysPerTree..ForestGrowth.MaxGrowthDays && it % ForestGrowth.DaysPerTree == 0
        }?.div(ForestGrowth.DaysPerTree)
    }

    fun chooseKind(random: Random = Random.Default): ForestTreeKind =
        ForestTreeKind.entries[random.nextInt(ForestTreeKind.entries.size)]

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
            kind = chooseKind(random),
            slotIndex = availableSlots[random.nextInt(availableSlots.size)],
            plantedAtEpochMillis = plantedAtEpochMillis,
        )
    }
}
