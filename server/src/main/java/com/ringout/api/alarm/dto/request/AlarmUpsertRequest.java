package com.ringout.api.alarm.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ringout.api.alarm.domain.AlarmDay;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;

public record AlarmUpsertRequest(
    @Schema(description = "알람 실행 시간 (HH:mm)", example = "07:00", type = "string",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    LocalTime time,

    @ArraySchema(
        arraySchema = @Schema(
            description = "알람이 실행되는 요일 목록. 중복 요일은 허용하지 않으며, 빈 배열은 반복 없는 알람입니다.",
            example = "[\"MONDAY\", \"FRIDAY\"]",
            requiredMode = Schema.RequiredMode.REQUIRED
        ),
        schema = @Schema(implementation = AlarmDay.class),
        uniqueItems = true
    )
    List<AlarmDay> dayOfWeek,

    @Schema(description = "알람 반복 시간(분). 0 이상 30 이하", example = "5", minimum = "0", maximum = "30",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Integer repeatTime,

    @Schema(description = "목적지 이름 또는 주소. 앞뒤 공백을 제거한 뒤 1자 이상 20자 이하", example = "세종대학교",
        maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
    String destinationName,

    @Schema(description = "목적지 위도", example = "37.5502", minimum = "-90.0", maximum = "90.0",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Double destinationLatitude,

    @Schema(description = "목적지 경도", example = "127.0738", minimum = "-180.0", maximum = "180.0",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Double destinationLongitude,

    @Schema(description = "알람 데이터 버전. 기존 알람이 있으면 서버 버전보다 높을 때만 전체 수정합니다.", example = "1",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Long alarmVersion
) {

}
