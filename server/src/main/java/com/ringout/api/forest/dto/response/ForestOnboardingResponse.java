package com.ringout.api.forest.dto.response;

import com.ringout.api.forest.domain.ForestOnboarding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ForestOnboardingResponse(
    @Schema(description = "서버에 최종 저장된 숲 온보딩 완료 시각", example = "2026-11-10T08:00:00+09:00")
    OffsetDateTime onboardingCompletedAt
) {

    public static ForestOnboardingResponse of(ForestOnboarding onboarding, ZoneId zoneId) {
        return new ForestOnboardingResponse(onboarding.getCompletedAt().atZone(zoneId).toOffsetDateTime());
    }
}
