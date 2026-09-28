package com.ringout.api.room.dto.request;

import com.ringout.api.room.domain.ActivityDay;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;

public record RoomCreateRequest(
    @Schema(
        description = "모임 방 이름. 앞뒤 공백은 제거되며, 한글·영문·숫자·공백만 사용할 수 있습니다.",
        example = "아침 운동 모임",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 2,
        maxLength = 20,
        pattern = "^[가-힣A-Za-z0-9 ]+$"
    )
    String name,

    @Schema(
        description = "모임 소개. 생략할 수 있지만, 제공하면 공백만으로 구성될 수 없고 최대 300자입니다.",
        example = "매주 함께 운동하고 인증하는 모임입니다.",
        maxLength = 300
    )
    String description,

    @ArraySchema(
        arraySchema = @Schema(
            description = "모임 활동 요일. 최소 1개를 선택하고 중복할 수 없습니다.",
            requiredMode = Schema.RequiredMode.REQUIRED
        ),
        schema = @Schema(implementation = ActivityDay.class),
        minItems = 1,
        uniqueItems = true
    )
    List<ActivityDay> activityDays,

    @Schema(
        description = "모임 활동 시간. ISO-8601 local time 형식으로 입력합니다.",
        example = "08:00",
        requiredMode = Schema.RequiredMode.REQUIRED,
        format = "time"
    )
    LocalTime activityTime
) {
}
