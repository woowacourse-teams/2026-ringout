package com.ringout.api.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

public record RoomUpdateRequest(
    @Schema(
        description = "변경할 모임 이름. 앞뒤 공백을 제거하며 한글·영문·숫자·공백만 허용합니다. 변경하지 않으면 null로 바인딩됩니다.",
        example = "새로운 아침 운동 모임",
        minLength = 2,
        maxLength = 20,
        pattern = "^[가-힣A-Za-z0-9 ]+$",
        nullable = true
    )
    String name,
    @Schema(
        description = "변경할 모임 소개. 최대 300자이며 빈 문자열을 전달하면 비웁니다. 변경하지 않으면 null로 바인딩됩니다.",
        example = "매주 아침 함께 운동하는 모임입니다.",
        maxLength = 300,
        nullable = true
    )
    String description,
    @Schema(
        description = "이미지 파일. 빈 파일 또는 image/*가 아닌 콘텐츠 타입은 거부됩니다. 전달하면 S3에 저장하고 대표 이미지를 교체합니다. 변경하지 않으면 파일 파트를 생략합니다.",
        format = "binary",
        nullable = true
    )
    MultipartFile image,
    @Schema(
        description = "true이면 현재 대표 이미지를 삭제하고 기본 이미지로 전환합니다. image와 함께 전달할 수 없습니다.",
        example = "false"
    )
    boolean removeImage
) {

    public RoomUpdateRequest(String name, String description, MultipartFile image) {
        this(name, description, image, false);
    }

    public boolean hasNoUpdateField() {
        return name == null && description == null && image == null && !removeImage;
    }

    public boolean hasInvalidImage() {
        return image != null && (image.isEmpty()
            || image.getContentType() == null
            || !image.getContentType().startsWith("image/"));
    }

    public boolean hasImageRemovalConflict() {
        return image != null && removeImage;
    }
}
