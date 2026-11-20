package com.ringout.api.forest.dto.response;

import com.ringout.api.forest.status.ForestSuccessStatus;

public record ForestOnboardingCompleteResult(
    ForestOnboardingResponse response,
    boolean created
) {

    public ForestSuccessStatus status() {
        if (created) {
            return ForestSuccessStatus.FOREST_ONBOARDING_COMPLETED;
        }
        return ForestSuccessStatus.FOREST_ONBOARDING_SYNCED;
    }
}
