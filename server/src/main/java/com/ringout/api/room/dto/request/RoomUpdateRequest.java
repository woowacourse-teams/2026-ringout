package com.ringout.api.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

public record RoomUpdateRequest(
    @Schema(
        description = "변경할 모임 이름. 변경하지 않으면 null로 바인딩됩니다.",
        example = "새로운 아침 운동 모임",
        maxLength = 20,
        nullable = true
    )
    String name,
    @Schema(
        description = "변경할 모임 소개. 변경하지 않으면 null로 바인딩됩니다.",
        example = "매주 아침 함께 운동하는 모임입니다.",
        maxLength = 300,
        nullable = true
    )
    String description,
    @Schema(
        description = "변경할 모임 대표 이미지. 변경하지 않으면 파일 파트를 생략해 null로 바인딩됩니다.",
        format = "binary",
        nullable = true
    )
    MultipartFile image
) {

    public boolean hasNoUpdateField() {
        return name == null && description == null && image == null;
    }

    public boolean hasInvalidImage() {
        return image != null && (image.isEmpty()
            || image.getContentType() == null
            || !image.getContentType().startsWith("image/"));
    }
}
