package com.ringout.api.destination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationUpdateRequest(
    @Schema(
        description = "수정할 목적지 별칭. 최대 12자까지 저장할 수 있습니다.",
        example = "회사",
        maxLength = 12,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    String alias,

    @Schema(
        description = "수정할 목적지 위도",
        example = "37.5665",
        minimum = "-90.0",
        maximum = "90.0",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    Double latitude,

    @Schema(
        description = "수정할 목적지 경도",
        example = "126.9780",
        minimum = "-180.0",
        maximum = "180.0",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    Double longitude
) {

    public boolean hasNoUpdateField() {
        return alias == null && latitude == null && longitude == null;
    }
}
