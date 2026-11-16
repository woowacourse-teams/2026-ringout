package com.ringout.api.forest.dto.response;

import com.ringout.api.forest.status.ForestSuccessStatus;

public record ForestTreeAcknowledgeResult(
    ForestTreeResponse response,
    boolean acknowledged
) {

    public ForestSuccessStatus status() {
        if (acknowledged) {
            return ForestSuccessStatus.FOREST_TREE_ACKNOWLEDGED;
        }
        return ForestSuccessStatus.FOREST_TREE_SYNCED;
    }
}
