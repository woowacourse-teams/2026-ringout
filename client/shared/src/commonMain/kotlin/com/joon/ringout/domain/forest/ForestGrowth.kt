package com.joon.ringout.domain.forest

/** Forest progress is capped independently of the lifetime arrival count. */
data class ForestGrowth(val successfulDayCount: Int) {
    init {
        require(successfulDayCount >= 0)
    }

    val growthDayCount: Int get() = successfulDayCount.coerceAtMost(MaxGrowthDays)
    val completedTreeCount: Int get() = growthDayCount / DaysPerTree
    val isComplete: Boolean get() = completedTreeCount == MaxTrees
    val hasGrowingSeed: Boolean get() = !isComplete

    // Trees are planted immediately, so day five already starts an empty next seed.
    val filledSlots: Int get() = if (isComplete) 0 else growthDayCount % DaysPerTree
    val remainingDays: Int get() = if (isComplete) 0 else DaysPerTree - filledSlots

    companion object {
        const val DaysPerTree = 5
        const val GridSize = 4
        const val MaxTrees = GridSize * GridSize
        const val MaxGrowthDays = DaysPerTree * MaxTrees
    }
}
