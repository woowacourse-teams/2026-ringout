package com.joon.ringout.data.alarmoccurrence

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidForestArrivalPlantingSyncTest {
    private val suite = ForestArrivalPlantingSyncTestSuite()

    @Test
    fun `도착마다 숲 판정을 끝내서 서버 성공일이 사일에서 육일로 바뀌어도 첫 나무를 놓치지 않는다`() =
        suite.`도착마다 숲 판정을 끝내서 서버 성공일이 사일에서 육일로 바뀌어도 첫 나무를 놓치지 않는다`()

    @Test
    fun `숲 조회 대기 중에는 다음 도착만 보류하고 울림 해제와 강제 종료는 전송한다`() =
        suite.`숲 조회 대기 중에는 다음 도착만 보류하고 울림 해제와 강제 종료는 전송한다`()

    @Test
    fun `실행 저장 순서와 달라도 실제 도착 시각이 이른 기록부터 전송한다`() =
        suite.`실행 저장 순서와 달라도 실제 도착 시각이 이른 기록부터 전송한다`()

    @Test
    fun `앞선 도착이 네트워크 재시도 중이면 다음 도착이 추월하지 않는다`() =
        suite.`앞선 도착이 네트워크 재시도 중이면 다음 도착이 추월하지 않는다`()

    @Test
    fun `획득 판단 기록 저장이 실패하면 도착을 완료 표시하지 않고 동일 시각으로 복구한다`() =
        suite.`획득 판단 기록 저장이 실패하면 도착을 완료 표시하지 않고 동일 시각으로 복구한다`()

    @Test
    fun `판단 기록 저장 뒤 완료 표시가 실패해도 재실행에서 같은 획득을 한 번만 처리한다`() =
        suite.`판단 기록 저장 뒤 완료 표시가 실패해도 재실행에서 같은 획득을 한 번만 처리한다`()

    @Test
    fun `도착 성공 시각이 없거나 다른 종료와 시각을 반환하면 식재를 시작하지 않는다`() =
        suite.`도착 성공 시각이 없거나 다른 종료와 시각을 반환하면 식재를 시작하지 않는다`()

    @Test
    fun `도착 응답 도중 계정이 바뀌면 이전 응답으로 획득을 기록하지 않는다`() =
        suite.`도착 응답 도중 계정이 바뀌면 이전 응답으로 획득을 기록하지 않는다`()

    @Test
    fun `도착 전송 대기열이 비어도 실행 루프가 남은 숲 판정을 복구하고 수동 재시도에 반응한다`() =
        suite.`도착 전송 대기열이 비어도 실행 루프가 남은 숲 판정을 복구하고 수동 재시도에 반응한다`()

    @Test
    fun `기존에 전송을 마친 도착은 식재 판단 기록으로 소급 이관하지 않는다`() =
        suite.`기존에 전송을 마친 도착은 식재 판단 기록으로 소급 이관하지 않는다`()

    @Test
    fun `숲 판정의 저장 오류는 다른 실행의 강제 종료를 막거나 도착을 잘못된 응답으로 바꾸지 않는다`() =
        suite.`숲 판정의 저장 오류는 다른 실행의 강제 종료를 막거나 도착을 잘못된 응답으로 바꾸지 않는다`()

    @Test
    fun `여러 전송기가 동시에 실행되어도 도착과 획득 판단을 중복하지 않는다`() =
        suite.`여러 전송기가 동시에 실행되어도 도착과 획득 판단을 중복하지 않는다`()
}
