package com.ringout.api.destination.dto.response;

import com.ringout.api.destination.domain.Destination;
import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationResponse(
    @Schema(description = "사용자별 목적지 고유 식별자", example = "destination-001")
    String destinationId,

    @Schema(description = "목적지 별칭", example = "런닝 장소")
    String alias,

    @Schema(description = "목적지 위도", example = "37.5665")
    Double latitude,

    @Schema(description = "목적지 경도", example = "126.9780")
    Double longitude,

    @Schema(description = "서버에 현재 저장된 목적지 버전", example = "1")
    Long destinationVersion
) {

    public static DestinationResponse from(Destination destination) {
        return new DestinationResponse(
            destination.getDestinationId(),
            destination.getAlias().getValue(),
            destination.getCoordinate().getLatitude(),
            destination.getCoordinate().getLongitude(),
            destination.getDestinationVersion()
        );
    }
}
