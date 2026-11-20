package com.ringout.api.forest.dto.response;

import com.ringout.api.forest.status.ForestSuccessStatus;

public record ForestTreePlantResult(
    ForestTreeResponse response,
    boolean created
) {

    public ForestSuccessStatus status() {
        if (created) {
            return ForestSuccessStatus.FOREST_TREE_PLANTED;
        }
        return ForestSuccessStatus.FOREST_TREE_SYNCED;
    }
}
