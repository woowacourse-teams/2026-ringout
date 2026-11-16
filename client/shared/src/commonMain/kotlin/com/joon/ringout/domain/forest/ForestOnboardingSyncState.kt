package com.joon.ringout.domain.forest

/** Device completion remains usable while its server confirmation is pending. */
enum class ForestOnboardingSyncState {
    Idle,
    Pending,
    Syncing,
    Synced,
    Blocked,
}
