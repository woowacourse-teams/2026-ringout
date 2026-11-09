package com.joon.ringout.data.forest

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidForestDatabaseTest {
    private val suite = ForestDatabaseTestSuite()

    @Test
    fun `숲을 열지 않아도 다섯째 날 자동으로 심고 여든한째 날에는 도착 기록만 늘어난다`() =
        suite.`숲을 열지 않아도 다섯째 날 자동으로 심고 여든한째 날에는 도착 기록만 늘어난다`()

    @Test
    fun `다섯째 날 동시 도착과 같은 회차 재시도는 나무를 한 번만 심는다`() =
        suite.`다섯째 날 동시 도착과 같은 회차 재시도는 나무를 한 번만 심는다`()

    @Test
    fun `날짜 사이에 공백과 월 변경이 있어도 성장하고 이전 이력과 실패는 제외한다`() =
        suite.`날짜 사이에 공백과 월 변경이 있어도 성장하고 이전 이력과 실패는 제외한다`()

    @Test
    fun `온보딩과 나무 안내 확인을 각각 저장하고 재시작과 재요청에서도 최초 확인을 유지한다`() =
        suite.`온보딩과 나무 안내 확인을 각각 저장하고 재시작과 재요청에서도 최초 확인을 유지한다`()

    @Test
    fun `나무 저장 실패는 도착 결과와 미션 시각까지 롤백하고 재시도하면 한 번만 심는다`() =
        suite.`나무 저장 실패는 도착 결과와 미션 시각까지 롤백하고 재시도하면 한 번만 심는다`()

    @Test
    fun `안내 확인 저장 실패는 미확인 상태를 보존하고 재시도할 수 있다`() =
        suite.`안내 확인 저장 실패는 미확인 상태를 보존하고 재시도할 수 있다`()

    @Test
    fun `데이터베이스가 같은 성장 회차와 같은 칸의 중복 나무를 거부한다`() =
        suite.`데이터베이스가 같은 성장 회차와 같은 칸의 중복 나무를 거부한다`()

    @Test
    fun `관찰자는 성장과 심은 나무가 일치하는 상태와 안내 확인 변경을 받는다`() =
        suite.`관찰자는 성장과 심은 나무가 일치하는 상태와 안내 확인 변경을 받는다`()

    @Test
    fun `버전 십사 업데이트에서 기존 도착과 확인 상태를 보존하고 획득한 나무만 반영한다`() =
        suite.`버전 십사 업데이트에서 기존 도착과 확인 상태를 보존하고 획득한 나무만 반영한다`()

    @Test
    fun `업데이트 전 성공일이 여든일을 넘어도 열여섯 그루까지만 소급한다`() =
        suite.`업데이트 전 성공일이 여든일을 넘어도 열여섯 그루까지만 소급한다`()

    @Test
    fun `업데이트 전 성공일이 다섯일 미만이면 씨앗 진행도만 유지한다`() =
        suite.`업데이트 전 성공일이 다섯일 미만이면 씨앗 진행도만 유지한다`()
}
