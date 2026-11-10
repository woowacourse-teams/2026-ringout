package com.ringout.api.destination.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DestinationListResponse(
    @Schema(description = "인증된 사용자가 소유한 전체 목적지 목록. destinationId 오름차순")
    List<DestinationResponse> destinations
) {

}
