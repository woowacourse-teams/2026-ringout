package com.ringout.api.destination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationUpsertRequest(
    @Schema(
        description = "목적지 별칭. 앞뒤 공백을 제거한 뒤 1자 이상 12자 이하여야 합니다.",
        example = "헬스장",
        maxLength = 12,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String alias,

    @Schema(
        description = "목적지 위도",
        example = "37.4979",
        minimum = "-90.0",
        maximum = "90.0",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Double latitude,

    @Schema(
        description = "목적지 경도",
        example = "127.0276",
        minimum = "-180.0",
        maximum = "180.0",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Double longitude,

    @Schema(
        description = "목적지 데이터 버전. 기존 목적지가 있으면 서버 버전보다 높을 때만 전체 수정합니다.",
        example = "2",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Long destinationVersion
) {

}
