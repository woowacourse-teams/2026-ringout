package com.ringout.api.alarm.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;

public record AlarmResponse(
    @Schema(description = "사용자별 알람 고유 식별자", example = "alarm-001")
    String alarmId,

    @Schema(description = "서버에 최종 저장된 알람 실행 시간 (HH:mm)", example = "07:00", type = "string")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    LocalTime time,

    @Schema(description = "서버에 최종 저장된 알람 실행 요일 목록. 월요일부터 일요일 순", example = "[\"MONDAY\", \"FRIDAY\"]")
    List<AlarmDay> dayOfWeek,

    @Schema(description = "서버에 최종 저장된 알람 반복 시간(분)", example = "5")
    int repeatTime,

    @Schema(description = "서버에 최종 저장된 목적지 이름 또는 주소", example = "세종대학교")
    String destinationName,

    @Schema(description = "서버에 최종 저장된 목적지 위도", example = "37.5502")
    double destinationLatitude,

    @Schema(description = "서버에 최종 저장된 목적지 경도", example = "127.0738")
    double destinationLongitude,

    @Schema(description = "서버에 최종 저장된 알람 버전", example = "1")
    long alarmVersion
) {

    public static AlarmResponse from(Alarm alarm) {
        return new AlarmResponse(
            alarm.alarmId(),
            alarm.alarmTime(),
            alarm.days(),
            alarm.repeatMinutes(),
            alarm.destinationName(),
            alarm.latitude(),
            alarm.longitude(),
            alarm.version()
        );
    }
}
