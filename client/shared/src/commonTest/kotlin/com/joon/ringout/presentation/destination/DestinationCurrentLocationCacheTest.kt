package com.joon.ringout.presentation.destination

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationCurrentLocationCacheTest {
    @Test
    fun `현재 위치를 구하기 전에는 초기 위치 캐시가 없다`() {
        assertNull(DestinationCurrentLocationCache().initialLocation())
    }

    @Test
    fun `재진입할 때 마지막 현재 위치를 주소 조회 전 초기 위치로 제공한다`() = runTest {
        val cache = DestinationCurrentLocationCache(testScheduler.timeSource)
        cache.update(latitude = 37.5665, longitude = 126.9780)

        advanceTimeBy(1_000)

        assertEquals(CurrentLocation, cache.initialLocation())
        assertEquals(CurrentLocation, cache.initialLocation())
    }

    @Test
    fun `수신한 지 삼 분인 현재 위치는 초기 위치로 사용할 수 있다`() = runTest {
        val cache = DestinationCurrentLocationCache(testScheduler.timeSource)
        cache.update(latitude = CurrentLocation.latitude, longitude = CurrentLocation.longitude)

        advanceTimeBy(DestinationLocationCacheMaxAgeMillis)

        assertEquals(CurrentLocation, cache.initialLocation())
    }

    @Test
    fun `삼 분을 초과한 현재 위치는 재진입 초기 위치로 사용하지 않는다`() = runTest {
        val cache = DestinationCurrentLocationCache(testScheduler.timeSource)
        cache.update(latitude = CurrentLocation.latitude, longitude = CurrentLocation.longitude)

        advanceTimeBy(DestinationLocationCacheMaxAgeMillis + 1)

        assertNull(cache.initialLocation())
    }

    @Test
    fun `새 현재 위치를 수신하면 좌표와 캐시 유지 시간을 갱신한다`() = runTest {
        val cache = DestinationCurrentLocationCache(testScheduler.timeSource)
        cache.update(latitude = CurrentLocation.latitude, longitude = CurrentLocation.longitude)
        advanceTimeBy(DestinationLocationCacheMaxAgeMillis)

        val updatedLocation = CurrentLocation.copy(latitude = 35.1796, longitude = 129.0756)
        cache.update(latitude = updatedLocation.latitude, longitude = updatedLocation.longitude)
        advanceTimeBy(DestinationLocationCacheMaxAgeMillis)

        assertEquals(updatedLocation, cache.initialLocation())
    }

    @Test
    fun `유효하지 않은 좌표는 기존 현재 위치와 캐시 만료 시간을 변경하지 않는다`() = runTest {
        val cache = DestinationCurrentLocationCache(testScheduler.timeSource)
        cache.update(latitude = CurrentLocation.latitude, longitude = CurrentLocation.longitude)
        advanceTimeBy(DestinationLocationCacheMaxAgeMillis)

        listOf(
            Double.NaN to 126.9780,
            37.5665 to Double.NaN,
            Double.POSITIVE_INFINITY to 126.9780,
            37.5665 to Double.NEGATIVE_INFINITY,
            90.1 to 126.9780,
            -90.1 to 126.9780,
            37.5665 to 180.1,
            37.5665 to -180.1,
        ).forEach { (latitude, longitude) ->
            cache.update(latitude, longitude)
            assertEquals(CurrentLocation, cache.initialLocation())
        }
        advanceTimeBy(1)

        assertNull(cache.initialLocation())
    }

    @Test
    fun `현재 위치 캐시를 비우면 초기 위치를 제공하지 않는다`() {
        val cache = DestinationCurrentLocationCache()
        cache.update(latitude = CurrentLocation.latitude, longitude = CurrentLocation.longitude)

        cache.clear()

        assertNull(cache.initialLocation())
    }
}

private val CurrentLocation = DestinationSelection(
    name = SelectedDestinationFallbackName,
    address = ResolvingDestinationAddress,
    latitude = 37.5665,
    longitude = 126.9780,
)
