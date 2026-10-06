package com.ringout.api.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RoomRecordsResponse(
    @Schema(description = "회원별 활동 기록") List<MemberRecordResponse> memberRecords
) {
}
