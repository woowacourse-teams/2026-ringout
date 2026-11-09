package com.joon.ringout.domain.forest

enum class ForestTreeKind(val persistedValue: String) {
    Pine("pine"),
    Oak("oak"),
    BigTree("big_tree"),
    ;

    companion object {
        fun fromPersistedValue(value: String): ForestTreeKind =
            entries.firstOrNull { it.persistedValue == value }
                ?: error("Unknown forest tree kind: $value")
    }
}

/** Existence means planted. Acknowledging the reveal never plants or re-rolls a tree. */
data class ForestTree(
    val growthCycle: Int,
    val kind: ForestTreeKind,
    val slotIndex: Int,
    val plantedAtEpochMillis: Long,
    val acknowledgedAtEpochMillis: Long? = null,
) {
    init {
        require(growthCycle in 1..ForestGrowth.MaxTrees)
        require(slotIndex in 0 until ForestGrowth.MaxTrees)
    }

    val row: Int get() = slotIndex / ForestGrowth.GridSize
    val column: Int get() = slotIndex % ForestGrowth.GridSize
}
