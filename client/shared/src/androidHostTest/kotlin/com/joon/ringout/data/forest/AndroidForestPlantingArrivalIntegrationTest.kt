package com.joon.ringout.data.forest

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidForestPlantingArrivalIntegrationTest {
    private val suite = ForestPlantingArrivalIntegrationTestSuite()

    @Test
    fun `재설치한 기기의 도착 하나로 서버의 다섯째 성공일 나무를 온보딩 전에 저장한다`() =
        suite.`재설치한 기기의 도착 하나로 서버의 다섯째 성공일 나무를 온보딩 전에 저장한다`()

    @Test
    fun `오프라인 도착을 시각순으로 조회하여 여섯째 날 전송 뒤에도 다섯째 날 나무를 저장한다`() =
        suite.`오프라인 도착을 시각순으로 조회하여 여섯째 날 전송 뒤에도 다섯째 날 나무를 저장한다`()

    @Test
    fun `획득 조회 실패 뒤 다음 도착은 기다리고 강제 종료는 전송한 뒤 재생성한 저장소로 복구한다`() =
        suite.`획득 조회 실패 뒤 다음 도착은 기다리고 강제 종료는 전송한 뒤 재생성한 저장소로 복구한다`()
}
