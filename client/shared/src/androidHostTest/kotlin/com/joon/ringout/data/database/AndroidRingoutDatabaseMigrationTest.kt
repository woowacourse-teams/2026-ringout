package com.joon.ringout.data.database

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidRingoutDatabaseMigrationTest {
    private val suite = RingoutDatabaseMigrationTestSuite()

    @Test
    fun `버전 십삼의 성공과 실패 기록을 보존하고 도착 성장은 빈 상태로 시작한다`() =
        suite.`버전 십삼의 성공과 실패 기록을 보존하고 도착 성장은 빈 상태로 시작한다`()

    @Test
    fun `마이그레이션으로 저장한 성장 활성화 기준은 재실행해도 바뀌지 않는다`() =
        suite.`마이그레이션으로 저장한 성장 활성화 기준은 재실행해도 바뀌지 않는다`()

    @Test
    fun `마이그레이션 이전 도착을 복구해도 성장하지 않고 활성화 시각부터 도착을 집계한다`() =
        suite.`마이그레이션 이전 도착을 복구해도 성장하지 않고 활성화 시각부터 도착을 집계한다`()

    @Test
    fun `버전 십이에서 label을 보정하고 알람과 연관 저장 데이터를 보존한다`() =
        suite.`버전 십이에서 label을 보정하고 알람과 연관 저장 데이터를 보존한다`()

    @Test
    fun `버전 십일에서 iOS 기록 판단 테이블을 추가하고 로컬 전용 결정을 보존한다`() =
        suite.`버전 십일에서 iOS 기록 판단 테이블을 추가하고 로컬 전용 결정을 보존한다`()

    @Test
    fun `버전 십의 미전송 알람을 보존하고 과거 이동 이벤트를 임의 생성하지 않는다`() =
        suite.`버전 십의 미전송 알람을 보존하고 과거 이동 이벤트를 임의 생성하지 않는다`()

    @Test
    fun `버전 구의 기존 데이터를 보존하고 빈 알람 전송 저장소를 추가한다`() =
        suite.`버전 구의 기존 데이터를 보존하고 빈 알람 전송 저장소를 추가한다`()

    @Test
    fun `버전 칠의 실행 시각을 보존하고 예정 시각은 추정하지 않은 채 새 열을 추가한다`() =
        suite.`버전 칠의 실행 시각을 보존하고 예정 시각은 추정하지 않은 채 새 열을 추가한다`()

    @Test
    fun `버전 육의 울림 이벤트에서 시작 시각만 복원하고 없는 종료와 완료 시각은 비워 둔다`() =
        suite.`버전 육의 울림 이벤트에서 시작 시각만 복원하고 없는 종료와 완료 시각은 비워 둔다`()

    @Test
    fun `버전 오에서 생성 집계 이벤트만 제거하고 울림과 기존 알람을 보존한다`() =
        suite.`버전 오에서 생성 집계 이벤트만 제거하고 울림과 기존 알람을 보존한다`()

    @Test
    fun `버전 사에서 기존 알람과 미션을 보존하고 활동 집계 테이블을 추가한다`() =
        suite.`버전 사에서 기존 알람과 미션을 보존하고 활동 집계 테이블을 추가한다`()

    @Test
    fun `버전 일에서 미션 기록을 보존하며 현재 버전으로 마이그레이션한다`() =
        suite.`버전 일에서 미션 기록을 보존하며 현재 버전으로 마이그레이션한다`()

    @Test
    fun `버전 이에서 기존 테이블을 보존하고 저장 목적지를 추가한다`() =
        suite.`버전 이에서 기존 테이블을 보존하고 저장 목적지를 추가한다`()

    @Test
    fun `버전 삼에서 기존 행을 보존하고 발생 ID 중복을 막는다`() =
        suite.`버전 삼에서 기존 행을 보존하고 발생 ID 중복을 막는다`()
}
