package com.joon.ringout.domain.forest

enum class ForestTreeAcknowledgementSyncState {
    Idle,
    Pending,
    Syncing,
    Synced,
    Blocked,
}
