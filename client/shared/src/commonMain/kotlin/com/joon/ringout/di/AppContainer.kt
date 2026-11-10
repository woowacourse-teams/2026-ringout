package com.joon.ringout.di

import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness
import com.joon.ringout.domain.push.PushTokenRegistrar
import com.joon.ringout.domain.push.PushTokenSource
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import com.joon.ringout.domain.room.RoomRepository
import com.joon.ringout.data.database.LocalUserDataCleanup
import com.joon.ringout.domain.outbox.EmptyOutboxReader
import com.joon.ringout.domain.outbox.OutboxReader

interface AppContainer {
    val termsRepository: com.joon.ringout.domain.terms.TermsRepository
    val networkMonitor: com.joon.ringout.domain.connectivity.NetworkMonitor
    val appPreferencesRepository: AppPreferencesRepository
    val systemThemeModeReader: SystemThemeModeReader
    val authSession: AuthSession
    val pushTokenSource: PushTokenSource
    val pushTokenRegistrationReadiness: PushTokenRegistrationReadiness
    val pushTokenRegistrar: PushTokenRegistrar
    val authRepository: AuthRepository
    val memberRepository: MemberRepository
    val roomRepository: RoomRepository
    val destinationRepository: DestinationRepository
    val alarmActivityRepository: AlarmActivityRepository
    val missionHistoryRepository: MissionHistoryRepository
    val localUserDataCleanup: LocalUserDataCleanup
    val arrivalResultRepository: com.joon.ringout.domain.arrival.ArrivalResultRepository
    val forestRepository: com.joon.ringout.domain.forest.ForestRepository
    val productAnalyticsRecorder: ProductAnalyticsRecorder
    val outboxReader: OutboxReader get() = EmptyOutboxReader
    val hasStoredAlarmsForEntryMigration: suspend () -> Boolean
    val currentVerifiedAlarmAccountId: suspend () -> String? get() = { null }
    val migrateLegacyAlarmData: suspend (String) -> Unit get() = {}
    fun onAppResumed() {}
}
