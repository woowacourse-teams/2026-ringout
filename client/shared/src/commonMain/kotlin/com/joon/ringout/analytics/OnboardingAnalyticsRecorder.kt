package com.joon.ringout.analytics

/** Stable wire values, independent of translated screen titles. */
enum class AnalyticsOnboardingStep(internal val wireName: String) {
    Introduction("introduction"),
    Destination("destination"),
    Interval("interval"),
    Departure("departure"),
}

interface OnboardingAnalyticsRecorder {
    fun recordOnboardingStarted(stepCount: Int)
    fun recordOnboardingStepViewed(step: AnalyticsOnboardingStep, stepCount: Int)
    fun recordOnboardingSubmitted(stepCount: Int)
    fun recordOnboardingCompleted(stepCount: Int)
}

internal object NoOpOnboardingAnalyticsRecorder : OnboardingAnalyticsRecorder {
    override fun recordOnboardingStarted(stepCount: Int) = Unit
    override fun recordOnboardingStepViewed(step: AnalyticsOnboardingStep, stepCount: Int) = Unit
    override fun recordOnboardingSubmitted(stepCount: Int) = Unit
    override fun recordOnboardingCompleted(stepCount: Int) = Unit
}

internal fun onboardingAnalyticsEvent(
    name: AnalyticsEventName,
    stepCount: Int,
    step: AnalyticsOnboardingStep? = null,
): AnalyticsEvent {
    require(stepCount == 4)
    return AnalyticsEvent(
        name = name,
        parameters = buildMap {
            put(AnalyticsParameterName.FlowName, AnalyticsParameterValue.Text("service_introduction"))
            put(AnalyticsParameterName.FlowVersion, AnalyticsParameterValue.Number(2))
            put(AnalyticsParameterName.StepCount, AnalyticsParameterValue.Number(stepCount.toLong()))
            if (step != null) {
                put(AnalyticsParameterName.StepName, AnalyticsParameterValue.Text(step.wireName))
                put(AnalyticsParameterName.StepIndex, AnalyticsParameterValue.Number(step.ordinal + 1L))
            }
        },
    )
}
