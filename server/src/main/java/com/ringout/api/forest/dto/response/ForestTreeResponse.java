package com.ringout.api.forest.dto.response;

import com.ringout.api.forest.domain.ForestTree;
import com.ringout.api.forest.domain.TreeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ForestTreeResponse(
    @Schema(description = "나무 획득 회차", example = "1")
    int growthCycle,
    @Schema(description = "서버에 최종 저장된 나무 종류", example = "pine", type = "string",
        allowableValues = {"pine", "oak", "big_tree"})
    TreeKind kind,
    @Schema(description = "서버가 배정한 칸 번호 (0~15). 행은 slotIndex / 4, 열은 slotIndex % 4", example = "7")
    int slotIndex,
    @Schema(description = "서버에 최종 저장된 나무 획득 시각", example = "2026-11-05T07:48:10+09:00")
    OffsetDateTime plantedAt,
    @Schema(description = "서버에 최종 저장된 연출 확인 시각. 미확인이면 null", example = "null", nullable = true)
    OffsetDateTime acknowledgedAt
) {

    public static ForestTreeResponse of(ForestTree tree, ZoneId zoneId) {
        return new ForestTreeResponse(
            tree.getGrowthCycle(),
            tree.getKind(),
            tree.getSlotIndex(),
            toOffsetDateTime(tree.getPlantedAt(), zoneId),
            toOffsetDateTime(tree.getAcknowledgedAt(), zoneId)
        );
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime, ZoneId zoneId) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(zoneId).toOffsetDateTime();
    }
}
