package com.joon.ringout.presentation.onboarding

import org.jetbrains.compose.resources.DrawableResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.onboarding_1
import ringout.shared.generated.resources.onboarding_2
import ringout.shared.generated.resources.onboarding_3
import ringout.shared.generated.resources.onboarding_4

data class OnboardingPageContent(
    val image: DrawableResource,
    val imageContentDescription: String,
    val title: String,
    val descriptionLines: List<OnboardingTextLine>,
)

data class OnboardingTextLine(
    val segments: List<OnboardingTextSegment>,
) {
    constructor(text: String) : this(listOf(OnboardingTextSegment(text)))
}

data class OnboardingTextSegment(
    val text: String,
    val isAccent: Boolean = false,
)

val defaultOnboardingPages = listOf(
    OnboardingPageContent(
        image = Res.drawable.onboarding_1,
        imageContentDescription = "목적지와 알람 간격을 설정하는 화면",
        title = "목적지와 알람 간격을 설정하세요",
        descriptionLines = listOf(
            OnboardingTextLine("목적지는 알람을 종료할 수 있는 장소예요."),
            OnboardingTextLine("목적지에 도착하지 않으면,"),
            OnboardingTextLine("일정한 간격으로 알람이 다시 울릴 수 있어요."),
        ),
    ),
    OnboardingPageContent(
        image = Res.drawable.onboarding_2,
        imageContentDescription = "설정한 알람을 끄고 출발하는 화면",
        title = "알람을 끄고 시작하세요",
        descriptionLines = listOf(
            OnboardingTextLine("설정한 시간이 되면 알람이 울릴거에요."),
            OnboardingTextLine("알람을 끄고 목적지로 이동을 시작하세요."),
        ),
    ),
    OnboardingPageContent(
        image = Res.drawable.onboarding_3,
        imageContentDescription = "목적지 도착으로 알람이 종료되는 화면",
        title = "목적지에 도착하면 끝이에요",
        descriptionLines = listOf(
            OnboardingTextLine("설정한 목적지에 도착하게 되면"),
            OnboardingTextLine("다음 알람 간격이 되어도"),
            OnboardingTextLine("더 이상 알람이 울리지 않아요."),
        ),
    ),
    OnboardingPageContent(
        image = Res.drawable.onboarding_4,
        imageContentDescription = "이동이 멈추면 알람이 다시 울리는 화면",
        title = "움직이지 않으면 다시 울려요",
        descriptionLines = listOf(
            OnboardingTextLine("만약 설정한 알람 간격동안 위치가 변하지 않으면,"),
            OnboardingTextLine(
                segments = listOf(
                    OnboardingTextSegment(text = "링아웃", isAccent = true),
                    OnboardingTextSegment("이 다시 알람을 울려드려요."),
                ),
            ),
        ),
    ),
)
