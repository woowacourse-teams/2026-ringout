package com.joon.ringout.domain.destination

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationDisplayNameTest {
    private val destination = SavedDestination(1, "헬스장", "대한민국 서울 서초구 반포동 748-10", 37.5, 127.0)

    @Test
    fun `즐겨찾기에 같은 별명과 위치가 있으면 별명을 표시한다`() {
        assertEquals("헬스장", displayName(listOf(destination)))
    }

    @Test
    fun `즐겨찾기가 삭제되면 국가 접두어를 제외한 주소를 표시한다`() {
        assertEquals("서울 서초구 반포동 748-10", displayName(emptyList()))
    }

    @Test
    fun `같은 별명의 다른 위치나 같은 위치의 다른 별명으로 대체하지 않는다`() {
        assertEquals("서울 서초구 반포동 748-10", displayName(listOf(destination.copy(latitude = 37.6))))
        assertEquals("서울 서초구 반포동 748-10", displayName(listOf(destination.copy(name = "회사"))))
    }

    @Test
    fun `즐겨찾기 조회 전에는 삭제된 것으로 판단하지 않는다`() {
        assertEquals("헬스장", displayName(null))
    }

    @Test
    fun `좌표 저장 과정의 작은 오차는 같은 위치로 판단한다`() {
        assertEquals("헬스장", displayName(listOf(destination.copy(latitude = 37.500001))))
    }

    @Test
    fun `주소가 없는 삭제된 즐겨찾기는 삭제된 별명 대신 주소 없음으로 표시한다`() {
        assertEquals("주소 정보 없음", resolveDestinationDisplayName("헬스장", " ", 37.5, 127.0, emptyList()))
    }

    private fun displayName(saved: List<SavedDestination>?): String = resolveDestinationDisplayName(
        destination.name, destination.address, destination.latitude, destination.longitude, saved,
    )
}
