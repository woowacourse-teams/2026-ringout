package com.joon.ringout.presentation.onboarding

import com.joon.ringout.analytics.AnalyticsEventName
import com.joon.ringout.analytics.AnalyticsOnboardingStep
import com.joon.ringout.analytics.AnalyticsParameterName
import com.joon.ringout.analytics.AnalyticsParameterValue
import com.joon.ringout.analytics.OnboardingAnalyticsFixture
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OnboardingAnalyticsFlowTest {
    @Test
    fun `다음 버튼과 뒤로 가기와 스와이프를 포함한 실제 페이지 진입만 기록한다`() {
        val fixture = OnboardingAnalyticsFixture()
        val viewModel = OnboardingViewModel(analytics = fixture.recorder)
        repeat(3) { viewModel.onStepVisible() }
        viewModel.requestNext()
        viewModel.onStepVisible()
        viewModel.back()
        viewModel.onStepVisible()
        viewModel.onPageChanged(2)
        repeat(2) { viewModel.onStepVisible() }
        viewModel.requestNext()
        viewModel.onStepVisible()

        assertEquals(1, fixture.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(
            listOf("introduction", "destination", "introduction", "interval", "departure"),
            fixture.events.filter { it.name == AnalyticsEventName.OnboardingStepViewed }.map {
                (it.parameters.getValue(AnalyticsParameterName.StepName) as AnalyticsParameterValue.Text).value
            },
        )
    }

    @Test
    fun `앱을 재시작하면 페이지 진입을 새로 기록하고 온보딩 시작은 중복 기록하지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        repeat(2) { OnboardingViewModel(analytics = fixture.recorder).onStepVisible() }
        assertEquals(1, fixture.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(2, fixture.events.count { it.name == AnalyticsEventName.OnboardingStepViewed })
    }

    @Test
    fun `수락한 완료와 재시도만 제출로 기록하고 저장 성공 전에는 온보딩 완료를 기록하지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        val viewModel = OnboardingViewModel(analytics = fixture.recorder)
        viewModel.onSubmitAccepted()
        assertEquals(0, fixture.events.size)
        viewModel.onPageChanged(3)
        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext())
        viewModel.onSubmitAccepted()
        viewModel.onSubmitAccepted()
        assertEquals(OnboardingAdvance.None, viewModel.requestNext())
        assertEquals(1, fixture.events.size)
        assertFalse(fixture.events.any { it.name == AnalyticsEventName.TutorialComplete })

        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext(1))
        viewModel.onSubmitAccepted()
        assertEquals(OnboardingAdvance.None, viewModel.requestNext(0))
        viewModel.onSubmitAccepted()
        assertEquals(
            listOf(AnalyticsEventName.OnboardingSubmit, AnalyticsEventName.OnboardingSubmit),
            fixture.events.map { it.name },
        )
    }

    @Test
    fun `완료 요청 뒤의 늦은 페이지 변경은 추가 진입 이벤트를 만들지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        val viewModel = OnboardingViewModel(analytics = fixture.recorder)
        viewModel.onPageChanged(3)
        viewModel.onStepVisible()
        val eventCount = fixture.events.size
        viewModel.requestNext()
        viewModel.onPageChanged(2)
        viewModel.back()
        viewModel.onStepVisible()
        assertEquals(eventCount, fixture.events.size)
    }

    @Test
    fun `분석 이벤트 기록에 실패해도 페이지 이동과 완료 요청은 계속된다`() {
        val viewModel = OnboardingViewModel(analytics = FailingOnboardingAnalyticsRecorder)
        viewModel.onStepVisible()
        viewModel.requestNext()
        assertEquals(OnboardingStep.Destination, viewModel.uiState.value.step)
        viewModel.onPageChanged(3)
        viewModel.onStepVisible()
        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext())
        viewModel.onSubmitAccepted()
        assertEquals(OnboardingAdvance.None, viewModel.requestNext())
    }
}

private object FailingOnboardingAnalyticsRecorder : OnboardingAnalyticsRecorder {
    override fun recordOnboardingStarted(stepCount: Int): Unit = error("analytics unavailable")
    override fun recordOnboardingStepViewed(step: AnalyticsOnboardingStep, stepCount: Int): Unit =
        error("analytics unavailable")
    override fun recordOnboardingSubmitted(stepCount: Int): Unit = error("analytics unavailable")
    override fun recordOnboardingCompleted(stepCount: Int): Unit = error("analytics unavailable")
}
