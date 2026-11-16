package com.ringout.api.forest.dto.response;

public record ForestOnboardingCompleteResult(
    ForestOnboardingResponse response,
    boolean created
) {
}
