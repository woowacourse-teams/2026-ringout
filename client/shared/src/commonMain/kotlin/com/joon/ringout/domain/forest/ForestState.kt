package com.joon.ringout.domain.forest

data class ForestState(
    val growth: ForestGrowth,
    val trees: List<ForestTree>,
    val onboardingCompletedAtEpochMillis: Long?,
) {
    val isOnboardingCompleted: Boolean get() = onboardingCompletedAtEpochMillis != null
    val pendingTrees: List<ForestTree>
        get() = trees.filter { it.acknowledgedAtEpochMillis == null }.sortedBy { it.growthCycle }
}
