package com.joon.ringout.presentation.onboarding

internal data class OnboardingPageContent(
    val title: String,
    val description: String,
)

internal fun OnboardingStep.pageContent(): OnboardingPageContent = when (this) {
    OnboardingStep.Introduction -> OnboardingPageContent(
        title = "알람을 끄고 다시 잠들었나요?",
        description = "링아웃은 목적지에 도착할 때까지\n설정한 간격으로 반복해서 울리는 알람이에요.",
    )
    OnboardingStep.Destination -> OnboardingPageContent(
        title = "아침을 시작할 곳을 정해요",
        description = "헬스장이나 공원처럼 잠에서 깨어나\n이동할 곳을 정해보세요.",
    )
    OnboardingStep.Interval -> OnboardingPageContent(
        title = "알람이 다시 울릴 간격을 정해요",
        description = "알람을 끈 뒤 정해둔 간격이 지나면\n링아웃이 다시 깨워드려요.",
    )
    OnboardingStep.Departure -> OnboardingPageContent(
        title = "알람을 끄고 출발해요",
        description = "알람을 끄고 목적지로 출발하세요.\n목적지에 도착하면 알람은 더 이상 울리지 않아요.",
    )
    OnboardingStep.LocationPermission -> OnboardingPageContent(
        title = "위치 권한을 허용해주세요",
        description = "목적지 알람에는 위치 권한을 사용해요.\n위치 권한을 설정해주세요!",
    )
    OnboardingStep.NotificationPermission -> OnboardingPageContent(
        title = "알림 권한을 허용해주세요",
        description = "목적지 도착 소식을 알려드릴게요.\n알림 권한을 설정해주세요!",
    )
}
