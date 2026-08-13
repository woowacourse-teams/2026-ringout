package com.ringout.api.destination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DestinationCreateRequest(
    @Schema(
        description = "목적지 별칭. 최대 12자까지 저장할 수 있습니다.",
        example = "헬스장",
        maxLength = 12,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "위치 별명은 1자 이상 12자 이하로 입력해야 합니다.")
    @Size(max = 12, message = "위치 별명은 1자 이상 12자 이하로 입력해야 합니다.")
    String alias,

    @Schema(
        description = "목적지 위도",
        example = "37.4979",
        minimum = "-90.0",
        maximum = "90.0",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    @DecimalMin(value = "-90.0", message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    @DecimalMax(value = "90.0", message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    Double latitude,

    @Schema(
        description = "목적지 경도",
        example = "127.0276",
        minimum = "-180.0",
        maximum = "180.0",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    @DecimalMin(value = "-180.0", message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    @DecimalMax(value = "180.0", message = "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다.")
    Double longitude
) {

}
