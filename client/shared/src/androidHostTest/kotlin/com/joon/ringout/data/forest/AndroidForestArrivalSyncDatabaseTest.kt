package com.joon.ringout.data.forest

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidForestArrivalSyncDatabaseTest {
    private val suite = ForestArrivalSyncDatabaseTestSuite()

    @Test
    fun `숲 갱신 횟수에는 현재 계정에서 전송 성공한 도착만 포함한다`() =
        suite.`숲 갱신 횟수에는 현재 계정에서 전송 성공한 도착만 포함한다`()

    @Test
    fun `오래된 도착이 늦게 전송되어도 관찰 횟수가 늘고 중복 성공은 늘지 않는다`() =
        suite.`오래된 도착이 늦게 전송되어도 관찰 횟수가 늘고 중복 성공은 늘지 않는다`()
}
