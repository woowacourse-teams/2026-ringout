package com.joon.ringout.data.missionhistory

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAlarmRecordLookupDatabaseTest {
    private val suite = AlarmRecordLookupDatabaseTestSuite()

    @Test
    fun `자정을 넘어 다음 달에 성공한 회차는 울린 날짜의 같은 알람 버전 그룹을 찾는다`() =
        suite.`자정을 넘어 다음 달에 성공한 회차는 울린 날짜의 같은 알람 버전 그룹을 찾는다`()

    @Test
    fun `울림 이벤트가 없는 성공 기록은 완료일에 조회하고 식별자가 없는 과거 기록도 그룹 키로 찾는다`() =
        suite.`울림 이벤트가 없는 성공 기록은 완료일에 조회하고 식별자가 없는 과거 기록도 그룹 키로 찾는다`()
}
