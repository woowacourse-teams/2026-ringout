package com.ringout.api.destination.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record DestinationCreateResponse(
    @Schema(description = "저장된 목적지 ID", example = "1")
    Long destinationId
) {

}
