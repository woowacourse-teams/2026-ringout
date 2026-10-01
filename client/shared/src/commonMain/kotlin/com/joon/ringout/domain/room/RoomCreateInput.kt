package com.joon.ringout.domain.room

data class RoomCreateInput(
    val name: String,
    val description: String?,
    val activityDays: List<String>,
    val activityTime: String,
) {
    init {
        require(name.isNotBlank()) { "모임 이름을 입력해 주세요." }
        require(description == null || description.isNotBlank()) { "모임 소개를 입력해 주세요." }
        require(description == null || description.length <= MaxDescriptionLength) {
            "모임 소개는 300자 이내로 작성해 주세요."
        }
        require(activityDays.isNotEmpty()) { "활동 요일을 선택해 주세요." }
        require(activityDays.distinct().size == activityDays.size) { "활동 요일은 중복될 수 없어요." }
        require(activityDays.all { it in ServerActivityDays }) { "활동 요일을 확인해 주세요." }
        require(TimePattern.matches(activityTime)) { "활동 시간을 확인해 주세요." }
    }
}

private const val MaxDescriptionLength = 300

private val ServerActivityDays = setOf(
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
)
private val TimePattern = Regex("""([01]\d|2[0-3]):[0-5]\d""")
