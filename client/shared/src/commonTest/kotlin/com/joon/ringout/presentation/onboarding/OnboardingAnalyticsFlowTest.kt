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
        repeat(3) { viewModel.onStepVisible(0) }
        viewModel.requestNext()
        viewModel.onStepVisible(1)
        viewModel.back()
        viewModel.onStepVisible(0)
        viewModel.onPageChanged(2)
        repeat(2) { viewModel.onStepVisible(2) }
        viewModel.requestNext()
        viewModel.onStepVisible(3)

        assertEquals(1, fixture.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(
            listOf("introduction", "destination", "introduction", "interval", "departure"),
            fixture.events.filter { it.name == AnalyticsEventName.OnboardingStepViewed }.map {
                (it.parameters.getValue(AnalyticsParameterName.StepName) as AnalyticsParameterValue.Text).value
            },
        )
    }

    @Test
    fun `이동 목표가 변경되어도 실제 도착한 페이지를 전달하기 전에는 조회를 기록하지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        val viewModel = OnboardingViewModel(analytics = fixture.recorder)
        viewModel.onStepVisible(0)
        repeat(3) { viewModel.requestNext() }
        // 이동 목표가 마지막 페이지여도 직전 페이지의 중복 보고는 새 조회가 아니다.
        viewModel.onStepVisible(0)
        assertEquals(1, fixture.events.count { it.name == AnalyticsEventName.OnboardingStepViewed })

        viewModel.onStepVisible(3)
        assertEquals(
            listOf("introduction", "departure"),
            fixture.events.filter { it.name == AnalyticsEventName.OnboardingStepViewed }.map {
                (it.parameters.getValue(AnalyticsParameterName.StepName) as AnalyticsParameterValue.Text).value
            },
        )
    }

    @Test
    fun `범위를 벗어난 페이지 표시는 시작과 조회 이벤트를 기록하지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        val viewModel = OnboardingViewModel(analytics = fixture.recorder)
        viewModel.onStepVisible(-1)
        viewModel.onStepVisible(6)
        assertEquals(emptyList(), fixture.events)
    }

    @Test
    fun `앱을 재시작하면 페이지 진입을 새로 기록하고 온보딩 시작은 중복 기록하지 않는다`() {
        val fixture = OnboardingAnalyticsFixture()
        repeat(2) { OnboardingViewModel(analytics = fixture.recorder).onStepVisible(0) }
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
        assertEquals(OnboardingAdvance.Complete, viewModel.completePermissions())
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
        viewModel.onStepVisible(3)
        val eventCount = fixture.events.size
        viewModel.completePermissions()
        viewModel.onPageChanged(2)
        viewModel.back()
        viewModel.onStepVisible(3)
        assertEquals(eventCount, fixture.events.size)
    }

    @Test
    fun `분석 이벤트 기록에 실패해도 페이지 이동과 완료 요청은 계속된다`() {
        val viewModel = OnboardingViewModel(analytics = FailingOnboardingAnalyticsRecorder)
        viewModel.onStepVisible(0)
        viewModel.requestNext()
        assertEquals(OnboardingStep.Destination, viewModel.uiState.value.step)
        viewModel.onPageChanged(3)
        viewModel.onStepVisible(3)
        assertEquals(OnboardingAdvance.Complete, viewModel.completePermissions())
        viewModel.onSubmitAccepted()
        assertEquals(OnboardingAdvance.None, viewModel.requestNext())
    }
}

private fun OnboardingViewModel.completePermissions(): OnboardingAdvance {
    val permissions = OnboardingPermissionState(OnboardingPermissionStatus.Granted, OnboardingPermissionStatus.Granted)
    onPermissionsRefreshed(permissions)
    onPageChanged(OnboardingStep.NotificationPermission.ordinal)
    requestNext()
    return onPermissionResult(OnboardingPermission.Notifications, uiState.value.permissionRequestId, permissions)
}

private object FailingOnboardingAnalyticsRecorder : OnboardingAnalyticsRecorder {
    override fun recordOnboardingStarted(stepCount: Int): Unit = error("analytics unavailable")
    override fun recordOnboardingStepViewed(step: AnalyticsOnboardingStep, stepCount: Int): Unit =
        error("analytics unavailable")
    override fun recordOnboardingSubmitted(stepCount: Int): Unit = error("analytics unavailable")
    override fun recordOnboardingCompleted(stepCount: Int): Unit = error("analytics unavailable")
}
