package com.joon.ringout.presentation.navigation

import kotlin.test.Test

class ArrivalNavigationEffectTest {
    private val suite = ArrivalNavigationEffectTestSuite()

    @Test
    fun `기록 공유는 미확인 도착이 없으면 유지하고 새 미션과 도착을 우선한다`() =
        suite.`기록 공유는 미확인 도착이 없으면 유지하고 새 미션과 도착을 우선한다`()

    @Test
    fun `미션이 먼저 사라져도 성공 결과가 저장되기 전에는 도착 화면을 열지 않는다`() =
        suite.`미션이 먼저 사라져도 성공 결과가 저장되기 전에는 도착 화면을 열지 않는다`()

    @Test
    fun `재시작과 반복 알림에서도 최신 미확인 결과 화면을 하나만 유지한다`() =
        suite.`재시작과 반복 알림에서도 최신 미확인 결과 화면을 하나만 유지한다`()

    @Test
    fun `새 알람과 진행 중 미션을 먼저 표시하고 끝나면 미확인 결과로 돌아온다`() =
        suite.`새 알람과 진행 중 미션을 먼저 표시하고 끝나면 미확인 결과로 돌아온다`()

    @Test
    fun `첫 조회가 끝나기 전에는 복원된 도착 화면과 푸시 요청을 유지한다`() =
        suite.`첫 조회가 끝나기 전에는 복원된 도착 화면과 푸시 요청을 유지한다`()

    @Test
    fun `도착 결과 확인 후 홈으로 돌아가며 다시 자동 표시하지 않는다`() =
        suite.`도착 결과 확인 후 홈으로 돌아가며 다시 자동 표시하지 않는다`()

    @Test
    fun `같은 활성 미션에서 홈으로 나온 선택은 미확인 결과가 있어도 유지한다`() =
        suite.`같은 활성 미션에서 홈으로 나온 선택은 미확인 결과가 있어도 유지한다`()

    @Test
    fun `미확인 도착 결과를 확인하기 전에는 모임 푸시를 소비하지 않는다`() =
        suite.`미확인 도착 결과를 확인하기 전에는 모임 푸시를 소비하지 않는다`()
}
