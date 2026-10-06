package com.joon.ringout.domain.room

const val MaxRoomImageBytes = 5L * 1024 * 1024

/** 미리보기용 비트맵이 아닌 원본 모임 이미지 파일. */
class RoomImageUpload(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
) {
    init {
        require(bytes.isNotEmpty() && bytes.size <= MaxRoomImageBytes) { "이미지는 최대 5MB까지 저장할 수 있어요." }
        require(contentType.matches(Regex("image/[a-zA-Z0-9.+-]+"))) { "이미지 형식을 확인해 주세요." }
        require(fileName.matches(Regex("[a-zA-Z0-9_.-]+"))) { "이미지 파일 이름을 확인해 주세요." }
    }
}
