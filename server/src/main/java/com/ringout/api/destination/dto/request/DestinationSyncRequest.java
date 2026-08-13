package com.ringout.api.destination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DestinationSyncRequest(
    @Schema(
        description = "서버에 저장할 기기 내 목적지 목록",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<DestinationSyncItemRequest> destinations
) {

    public boolean hasNoDestinationList() {
        return destinations == null;
    }

    public record DestinationSyncItemRequest(
        @Schema(description = "기기에서 생성한 목적지 고유 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Long clientDestinationId,

        @Schema(description = "목적지 별칭", example = "집", maxLength = 12, requiredMode = Schema.RequiredMode.REQUIRED)
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
        Double longitude
    ) {
    }
}
