package com.ringout.api.destination.dto.response;

import com.ringout.api.destination.domain.Destination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DestinationSyncResponse(
    @Schema(description = "동기화된 목적지 목록")
    List<DestinationSyncItemResponse> destinations
) {

    public record DestinationSyncItemResponse(
        @Schema(description = "기기에서 생성한 목적지 고유 ID", example = "2")
        Long clientDestinationId,

        @Schema(description = "서버에 저장된 목적지 ID", example = "1")
        Long destinationId,

        @Schema(description = "목적지 별칭", example = "집")
        String alias,

        @Schema(description = "목적지 위도", example = "37.4979")
        Double latitude,

        @Schema(description = "목적지 경도", example = "127.0276")
        Double longitude
    ) {

        public static DestinationSyncItemResponse of(Long clientDestinationId, Destination destination) {
            return new DestinationSyncItemResponse(
                clientDestinationId,
                destination.getId(),
                destination.getAlias().getValue(),
                destination.getCoordinate().getLatitude(),
                destination.getCoordinate().getLongitude()
            );
        }
    }
}
