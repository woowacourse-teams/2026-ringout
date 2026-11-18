package com.joon.ringout.domain.arrival

/** Each distinct successful arrival date fills one slot, regardless of gaps between dates. */
data class ArrivalGrowth(
    val successfulDayCount: Int,
) {
    init {
        require(successfulDayCount >= 0) { "Successful arrival day count must not be negative." }
    }

    // Keep the completed tree visible on the fifth day; start the next seed on the sixth.
    val filledSlots: Int
        get() = if (successfulDayCount == 0) 0 else (successfulDayCount - 1) % DaysPerTree + 1

    val remainingDays: Int
        get() = DaysPerTree - filledSlots

    val completedTreeCount: Int
        get() = successfulDayCount / DaysPerTree

    val isTreeComplete: Boolean
        get() = successfulDayCount > 0 && successfulDayCount % DaysPerTree == 0

    private companion object {
        const val DaysPerTree = 5
    }
}
