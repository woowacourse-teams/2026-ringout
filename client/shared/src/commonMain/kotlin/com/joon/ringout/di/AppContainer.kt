package com.joon.ringout.di

import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.SystemThemeModeReader

interface AppContainer {
    val appPreferencesRepository: AppPreferencesRepository
    val systemThemeModeReader: SystemThemeModeReader
    val authSession: AuthSession
    val authRepository: AuthRepository
    val memberRepository: MemberRepository
    val destinationRepository: DestinationRepository
    val missionHistoryRepository: MissionHistoryRepository
    val productAnalyticsRecorder: ProductAnalyticsRecorder
}
