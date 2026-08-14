package com.ringout.api.destination.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationUpdateResponse(
    @Schema(description = "수정된 목적지 ID", example = "1")
    Long destinationId,

    @Schema(description = "수정된 목적지 별칭", example = "회사")
    String alias,

    @Schema(description = "수정된 목적지 위도", example = "37.5665")
    Double latitude,

    @Schema(description = "수정된 목적지 경도", example = "126.9780")
    Double longitude
) {

}
