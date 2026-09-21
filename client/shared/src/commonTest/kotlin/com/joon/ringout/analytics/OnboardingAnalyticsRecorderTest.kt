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
                    AnalyticsParameterName.FlowName to AnalyticsParameterValue.Text("first_alarm"),
                    AnalyticsParameterName.FlowVersion to AnalyticsParameterValue.Number(1),
                    AnalyticsParameterName.StepCount to AnalyticsParameterValue.Number(4),
                ),
                event.parameters,
            )
        }
    }

    @Test
    fun `단계 조회와 제출 이벤트는 허용된 매개변수만 사용하고 플랫폼별 마지막 단계를 반영한다`() {
        for (count in listOf(4, 5)) {
            val f = OnboardingAnalyticsFixture()
            AnalyticsOnboardingStep.entries.take(count).forEach { step ->
                f.recorder.recordOnboardingStepViewed(step, count)
            }
            repeat(2) { f.recorder.recordOnboardingSubmitted(count) }
            assertEquals(count + 2, f.events.size)
            f.events.forEachIndexed { index, event ->
                val expectedIndex = (index + 1).coerceAtMost(count)
                assertEquals(
                    setOf(
                        AnalyticsParameterName.FlowName, AnalyticsParameterName.FlowVersion,
                        AnalyticsParameterName.StepCount, AnalyticsParameterName.StepName,
                        AnalyticsParameterName.StepIndex,
                    ),
                    event.parameters.keys,
                )
                assertEquals(AnalyticsParameterValue.Number(expectedIndex.toLong()), event.parameters[AnalyticsParameterName.StepIndex])
                assertEquals(AnalyticsParameterValue.Text(AnalyticsOnboardingStep.entries[expectedIndex - 1].wireName), event.parameters[AnalyticsParameterName.StepName])
            }
            assertEquals("onboarding_submit", f.events.last().name.wireName)
        }
    }

    @Test
    fun `유효하지 않은 흐름 정보는 이벤트를 전송하지 않고 시작 이벤트 기록 기회를 소진하지 않는다`() {
        val f = OnboardingAnalyticsFixture()
        f.recorder.recordOnboardingStarted(3)
        f.recorder.recordOnboardingStepViewed(AnalyticsOnboardingStep.Sound, 4)
        f.recorder.recordOnboardingSubmitted(6)
        f.recorder.recordOnboardingCompleted(0)
        assertTrue(f.events.isEmpty())
        f.recorder.recordOnboardingStarted(5)
        assertEquals(1, f.events.size)
    }

    @Test
    fun `이벤트 추적기와 영구 저장소에 오류가 발생해도 온보딩은 중단되지 않는다`() {
        val trackerFailure = DefaultProductAnalyticsRecorder(
            AnalyticsTracker { error("offline") }, InMemoryProductAnalyticsUsageStore(),
        )
        trackerFailure.recordOnboardingStarted(5)
        trackerFailure.recordOnboardingStepViewed(AnalyticsOnboardingStep.Time, 5)
        trackerFailure.recordOnboardingSubmitted(5)
        trackerFailure.recordOnboardingCompleted(5)
        val f = OnboardingAnalyticsFixture()
        val storeFailure = DefaultProductAnalyticsRecorder(f.tracker, object : ProductAnalyticsUsageStore {
            override fun claimDestinationCreation(destinationKey: String): Long? = null
            override fun claimOnboardingEvent(eventName: AnalyticsEventName): Boolean = error("disk failure")
        })
        storeFailure.recordOnboardingStarted(5)
        storeFailure.recordOnboardingCompleted(5)
        assertTrue(f.events.isEmpty())
    }
}
