package com.ringout.api.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RoomMembersResponse(
    @Schema(description = "현재 모임에 참여 중인 회원 목록") List<RoomManagementMemberResponse> members
) {
}
