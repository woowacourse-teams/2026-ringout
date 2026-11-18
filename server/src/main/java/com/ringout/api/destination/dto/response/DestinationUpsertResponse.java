package com.ringout.api.destination.dto.response;

import com.ringout.api.destination.domain.Destination;
import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationUpsertResponse(
    @Schema(description = "클라이언트가 생성한 목적지 ID", example = "destination-001")
    String destinationId,

    @Schema(description = "서버에 최종 저장된 목적지 별칭", example = "헬스장")
    String alias,

    @Schema(description = "서버에 최종 저장된 목적지 위도", example = "37.4979")
    Double latitude,

    @Schema(description = "서버에 최종 저장된 목적지 경도", example = "127.0276")
    Double longitude,

    @Schema(description = "서버에 최종 저장된 목적지 버전", example = "2")
    Long destinationVersion
) {

    public static DestinationUpsertResponse from(Destination destination) {
        return new DestinationUpsertResponse(
            destination.getDestinationId(),
            destination.getAlias().getValue(),
            destination.getCoordinate().getLatitude(),
            destination.getCoordinate().getLongitude(),
            destination.getDestinationVersion()
        );
    }
}
