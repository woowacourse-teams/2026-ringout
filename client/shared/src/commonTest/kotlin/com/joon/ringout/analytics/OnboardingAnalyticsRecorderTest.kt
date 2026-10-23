package com.joon.ringout.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingAnalyticsRecorderTest {
    @Test
    fun `기록기를 재생성해도 온보딩 시작과 완료 이벤트는 중복 기록되지 않는다`() {
        val f = OnboardingAnalyticsFixture()
        repeat(2) {
            val recorder = DefaultProductAnalyticsRecorder(f.tracker, f.store)
            recorder.recordOnboardingStarted(4)
            recorder.recordOnboardingCompleted(4)
        }
        assertEquals(listOf("tutorial_begin", "tutorial_complete"), f.events.map { it.name.wireName })
        f.events.forEach { event ->
            assertEquals(
                mapOf(
                    AnalyticsParameterName.FlowName to AnalyticsParameterValue.Text("service_introduction"),
                    AnalyticsParameterName.FlowVersion to AnalyticsParameterValue.Number(2),
                    AnalyticsParameterName.StepCount to AnalyticsParameterValue.Number(4),
                ),
                event.parameters,
            )
        }
    }

    @Test
    fun `네 소개 페이지의 조회와 마지막 출발 페이지의 제출을 기록한다`() {
        val f = OnboardingAnalyticsFixture()
        val steps = listOf(
            AnalyticsOnboardingStep.Introduction,
            AnalyticsOnboardingStep.Destination,
            AnalyticsOnboardingStep.Interval,
            AnalyticsOnboardingStep.Departure,
        )
        steps.forEach { step -> f.recorder.recordOnboardingStepViewed(step, 4) }
        repeat(2) { f.recorder.recordOnboardingSubmitted(4) }

        assertEquals(
            listOf(
                "onboarding_step_viewed", "onboarding_step_viewed",
                "onboarding_step_viewed", "onboarding_step_viewed",
                "onboarding_submit", "onboarding_submit",
            ),
            f.events.map { it.name.wireName },
        )
        val expectedNames = listOf("introduction", "destination", "interval", "departure", "departure", "departure")
        f.events.forEachIndexed { index, event ->
            assertEquals(
                mapOf(
                    AnalyticsParameterName.FlowName to AnalyticsParameterValue.Text("service_introduction"),
                    AnalyticsParameterName.FlowVersion to AnalyticsParameterValue.Number(2),
                    AnalyticsParameterName.StepCount to AnalyticsParameterValue.Number(4),
                    AnalyticsParameterName.StepName to AnalyticsParameterValue.Text(expectedNames[index]),
                    AnalyticsParameterName.StepIndex to AnalyticsParameterValue.Number((index + 1L).coerceAtMost(4)),
                ),
                event.parameters,
            )
        }
    }

    @Test
    fun `이전 버전의 시작과 완료 기록이 있으면 새 소개 흐름에서도 다시 기록하지 않는다`() {
        val f = OnboardingAnalyticsFixture()
        assertTrue(f.store.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
        assertTrue(f.store.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))

        f.recorder.recordOnboardingStarted(4)
        f.recorder.recordOnboardingCompleted(4)

        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `이미 조회한 페이지에 다시 진입하면 조회를 추가로 기록한다`() {
        val f = OnboardingAnalyticsFixture()
        listOf(
            AnalyticsOnboardingStep.Introduction,
            AnalyticsOnboardingStep.Destination,
            AnalyticsOnboardingStep.Introduction,
        ).forEach { f.recorder.recordOnboardingStepViewed(it, 4) }

        assertEquals(
            listOf("introduction", "destination", "introduction").map { AnalyticsParameterValue.Text(it) },
            f.events.map { it.parameters[AnalyticsParameterName.StepName] },
        )
    }

    @Test
    fun `유효하지 않은 흐름 정보는 이벤트를 전송하지 않고 시작 이벤트 기록 기회를 소진하지 않는다`() {
        val f = OnboardingAnalyticsFixture()
        for (count in listOf(0, 3, 5, 6)) {
            f.recorder.recordOnboardingStarted(count)
            f.recorder.recordOnboardingStepViewed(AnalyticsOnboardingStep.Introduction, count)
            f.recorder.recordOnboardingSubmitted(count)
            f.recorder.recordOnboardingCompleted(count)
        }
        assertTrue(f.events.isEmpty())
        f.recorder.recordOnboardingStarted(4)
        f.recorder.recordOnboardingCompleted(4)
        assertEquals(listOf("tutorial_begin", "tutorial_complete"), f.events.map { it.name.wireName })
    }

    @Test
    fun `이벤트 추적기와 영구 저장소에 오류가 발생해도 온보딩은 중단되지 않는다`() {
        val trackerFailure = DefaultProductAnalyticsRecorder(
            AnalyticsTracker { error("offline") }, InMemoryProductAnalyticsUsageStore(),
        )
        trackerFailure.recordOnboardingStarted(4)
        trackerFailure.recordOnboardingStepViewed(AnalyticsOnboardingStep.Introduction, 4)
        trackerFailure.recordOnboardingSubmitted(4)
        trackerFailure.recordOnboardingCompleted(4)
        val f = OnboardingAnalyticsFixture()
        val storeFailure = DefaultProductAnalyticsRecorder(f.tracker, object : ProductAnalyticsUsageStore {
            override fun claimDestinationCreation(destinationKey: String): Long? = null
            override fun claimOnboardingEvent(eventName: AnalyticsEventName): Boolean = error("disk failure")
        })
        storeFailure.recordOnboardingStarted(4)
        storeFailure.recordOnboardingCompleted(4)
        assertTrue(f.events.isEmpty())
    }
}
