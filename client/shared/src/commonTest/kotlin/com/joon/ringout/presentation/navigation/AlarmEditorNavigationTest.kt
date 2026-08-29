package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.AppScreen
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.AnalyticsTracker
import com.joon.ringout.analytics.DefaultProductAnalyticsRecorder
import com.joon.ringout.analytics.ProductAnalyticsUsageStore
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.toDestinationSelection
import com.joon.ringout.resolveAppScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditorNavigationTest {
    @Test
    fun `생성과 수정 진입은 해당 경로에 맞는 초안을 준비한다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)

        fixture.navigation.startCreating("07:45")

        assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.state.backStack.toList())
        assertEquals("07:45", fixture.alarmSetup.uiState.time)
        assertTrue(fixture.navigation.hasValidDraft())
        fixture.navigation.startEditing(SavedAlarm)

        assertEquals(
            listOf(AppRoute.Home, AppRoute.EditAlarm(SavedAlarm.id)),
            fixture.state.backStack.toList(),
        )
        assertEquals(SavedAlarm.id, fixture.alarmSetup.uiState.alarmId)
        assertTrue(fixture.navigation.hasValidDraft())
    }

    @Test
    fun `저장된 목적지는 다시 저장하지 않고 기존 편집 초안에 반영한다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        fixture.alarmSetup.updateMinute(45)
        fixture.alarmSetup.updateLimitMinutes(20)
        val draft = fixture.alarmSetup.uiState
        val route = fixture.openDestination()

        fixture.navigation.onSavedDestinationSelected(route, AppScreen.Destination, SelectedDestination)

        assertEquals(
            draft.copy(destination = SelectedDestination.toDestinationSelection()),
            fixture.alarmSetup.uiState,
        )
        assertEquals(
            listOf(AppRoute.Home, AppRoute.EditAlarm(SavedAlarm.id)),
            fixture.state.backStack.toList(),
        )
        assertTrue(fixture.repository.saveRequests.isEmpty())
    }

    @Test
    fun `목적지에 재진입하면 이전 화면의 선택 콜백을 새 요청에 적용하지 않는다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        val firstRoute = fixture.openDestination()
        fixture.navigation.onBack(firstRoute, AppScreen.Destination)
        val secondRoute = fixture.openDestination()
        val draft = fixture.alarmSetup.uiState

        assertTrue(secondRoute.requestId > firstRoute.requestId)
        fixture.navigation.onSavedDestinationSelected(firstRoute, AppScreen.Destination, SelectedDestination)

        assertEquals(secondRoute, fixture.state.backStack.last())
        assertEquals(draft, fixture.alarmSetup.uiState)
    }

    @Test
    fun `알람음 선택을 취소하면 유지하고 현재 화면에서 확정할 때만 반영한다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        val parent = AppRoute.EditAlarm(SavedAlarm.id)
        val originalSound = fixture.alarmSetup.uiState.alarmSound
        fixture.navigation.onAlarmSoundClick(parent, AppScreen.EditAlarm)

        fixture.navigation.onBack(AppRoute.AlarmSound, AppScreen.AlarmSound)

        assertEquals(originalSound, fixture.alarmSetup.uiState.alarmSound)
        assertEquals(parent, fixture.state.backStack.last())
        fixture.navigation.onAlarmSoundClick(parent, AppScreen.EditAlarm)
        fixture.navigation.onAlarmSoundSelected(AppScreen.AlarmRinging, SelectedSound)
        assertEquals(originalSound, fixture.alarmSetup.uiState.alarmSound)
        assertEquals(AppRoute.AlarmSound, fixture.state.backStack.last())

        fixture.navigation.onAlarmSoundSelected(AppScreen.AlarmSound, SelectedSound)

        assertEquals(SelectedSound, fixture.alarmSetup.uiState.alarmSound)
        assertEquals(parent, fixture.state.backStack.last())
        fixture.navigation.onAlarmSoundSelected(AppScreen.AlarmSound, originalSound)
        assertEquals(SelectedSound, fixture.alarmSetup.uiState.alarmSound)
    }

    @Test
    fun `알람 저장 처리 중에는 생성과 수정 화면의 뒤로 가기를 막는다`() = runTest {
        for (isEditing in listOf(false, true)) {
            val fixture = AlarmEditorFixture(backgroundScope)
            if (isEditing) {
                fixture.navigation.startEditing(SavedAlarm)
            } else {
                fixture.navigation.startCreating("07:00")
                fixture.alarmSetup.updateDestination(OriginalDestination.toDestinationSelection())
            }
            val parent = assertNotNull(fixture.state.editorRoute)
            val screen = fixture.state.requestedScreen
            assertTrue(fixture.alarmSetup.requestSave())

            assertTrue(fixture.navigation.isBackBlocked(screen))
            fixture.navigation.onBack(parent, screen)

            assertEquals(parent, fixture.state.backStack.last())
            fixture.alarmSetup.resetSaveFlow()
            assertFalse(fixture.navigation.isBackBlocked(screen))
            fixture.navigation.onBack(parent, screen)
            assertEquals(AppScreen.Home, fixture.state.requestedScreen)
        }
    }

    @Test
    fun `목적지 저장 중 뒤로 가도 저장은 계속하지만 이탈한 요청 결과는 소비만 한다`() = runTest {
        for (reenterDestination in listOf(false, true)) {
            val fixture = AlarmEditorFixture(backgroundScope)
            fixture.navigation.startEditing(SavedAlarm)
            val draft = fixture.alarmSetup.uiState
            val firstRoute = fixture.openDestination()
            val saveGate = CompletableDeferred<Unit>()
            fixture.repository.saveGate = saveGate
            fixture.saveDestination(firstRoute)
            runCurrent()
            assertTrue(fixture.destination.uiState.isSaving)

            assertFalse(fixture.navigation.isBackBlocked(AppScreen.Destination))
            fixture.navigation.onBack(firstRoute, AppScreen.Destination)

            assertEquals(AppScreen.EditAlarm, fixture.state.requestedScreen)
            assertTrue(fixture.destination.uiState.isSaving)
            if (reenterDestination) fixture.openDestination()
            val expectedStack = fixture.state.backStack.toList()
            saveGate.complete(Unit)
            runCurrent()
            val event = assertNotNull(fixture.destination.uiState.savedEvent)
            assertEquals(firstRoute.requestId, event.requestId)

            fixture.navigation.onDestinationSaved(event)

            assertEquals(expectedStack, fixture.state.backStack.toList())
            assertEquals(draft, fixture.alarmSetup.uiState)
            assertNull(fixture.destination.uiState.savedEvent)
            assertEquals(1, fixture.repository.saveRequests.size)
        }
    }

    @Test
    fun `알람이 덮어도 현재 목적지 요청의 저장 결과는 부모 초안에 반영한다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        val route = fixture.openDestination()
        fixture.saveDestination(route)
        runCurrent()
        val event = assertNotNull(fixture.destination.uiState.savedEvent)
        val visibleScreen = resolveAppScreen(
            requestedScreen = fixture.state.requestedScreen,
            hasRingingAlarm = true,
            hasActiveAlarmMission = false,
            authSessionState = AuthSessionState.Unauthenticated,
        )
        assertEquals(AppScreen.AlarmRinging, visibleScreen)

        fixture.navigation.onDestinationSaved(event)

        assertEquals(SelectedDestination.toDestinationSelection(), fixture.alarmSetup.uiState.destination)
        assertEquals(
            listOf(AppRoute.Home, AppRoute.EditAlarm(SavedAlarm.id)),
            fixture.state.backStack.toList(),
        )
        assertNull(fixture.destination.uiState.savedEvent)
    }

    @Test
    fun `요청 식별자가 맞아도 복원된 초안이 없으면 저장 결과를 적용하지 않는다`() = runTest {
        val route = AppRoute.Destination(12L)
        val fixture = AlarmEditorFixture(
            backgroundScope,
            restoredState(AppRoute.Home, AppRoute.AddAlarm, route),
        )
        fixture.saveDestination(route)
        runCurrent()
        val event = assertNotNull(fixture.destination.uiState.savedEvent)

        fixture.navigation.onDestinationSaved(event)

        assertNull(fixture.alarmSetup.uiState.destination)
        assertEquals(route, fixture.state.backStack.last())
        assertNull(fixture.destination.uiState.savedEvent)
    }

    @Test
    fun `초안 없이 복원한 편집 경로는 우선 화면을 건드리지 않고 복귀 시 홈으로 이동한다`() = runTest {
        val paths = listOf(
            listOf(AppRoute.Home, AppRoute.AddAlarm),
            listOf(AppRoute.Home, AppRoute.EditAlarm(SavedAlarm.id)),
            listOf(AppRoute.Home, AppRoute.AddAlarm, AppRoute.Destination(3L)),
            listOf(AppRoute.Home, AppRoute.EditAlarm(SavedAlarm.id), AppRoute.AlarmSound),
        )
        for (path in paths) {
            val fixture = AlarmEditorFixture(backgroundScope, restoredState(*path.toTypedArray()))
            val editorScreen = fixture.state.requestedScreen
            assertFalse(fixture.navigation.hasValidDraft())

            fixture.navigation.onMissingDraft(AppScreen.AlarmRinging)
            assertEquals(path, fixture.state.backStack.toList())
            val activeMission = AppRoute.ActiveAlarmTracking("occurrence-1")
            fixture.state.navigate(activeMission)
            fixture.navigation.onMissingDraft(editorScreen)
            assertEquals(AppScreen.ActiveAlarmTracking, fixture.state.requestedScreen)
            assertEquals(path + activeMission, fixture.state.backStack.toList())

            fixture.state.navigate(path.last())
            fixture.navigation.onMissingDraft(editorScreen)

            assertEquals(listOf(AppRoute.Home), fixture.state.backStack.toList())
        }
    }

    @Test
    fun `수정 경로의 알람 식별자가 초안과 다르면 이전 알람을 수정하지 않고 홈으로 이동한다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        fixture.state.navigate(AppRoute.EditAlarm("another-alarm"))

        assertFalse(fixture.navigation.hasValidDraft())
        fixture.navigation.onMissingDraft(AppScreen.EditAlarm)

        assertEquals(AppScreen.Home, fixture.state.requestedScreen)
        assertEquals(SavedAlarm.id, fixture.alarmSetup.uiState.alarmId)
    }

    @Test
    fun `백스택 복원 후 같은 ViewModel 초안을 유지하고 목적지 요청 식별자를 이어간다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        fixture.alarmSetup.updateMinute(45)
        fixture.alarmSetup.updateLimitMinutes(23)
        val draft = fixture.alarmSetup.uiState
        val parent = AppRoute.EditAlarm(SavedAlarm.id)
        val previousRoute = AppRoute.Destination(41L)
        val restored = restoredState(AppRoute.Home, parent, previousRoute)
        val navigation = AlarmEditorNavigation(restored, fixture.alarmSetup, fixture.destination)

        navigation.onMissingDraft(AppScreen.Destination)

        assertTrue(navigation.hasValidDraft())
        assertEquals(previousRoute, restored.backStack.last())
        assertEquals(draft, fixture.alarmSetup.uiState)
        navigation.onBack(previousRoute, AppScreen.Destination)
        navigation.onDestinationClick(parent, AppScreen.EditAlarm)

        val nextRoute = assertIs<AppRoute.Destination>(restored.backStack.last())
        assertTrue(nextRoute.requestId > previousRoute.requestId)
        assertEquals(draft, fixture.alarmSetup.uiState)
    }

    @Test
    fun `가려진 편집 화면과 다른 알람의 이전 콜백은 선택 화면을 열지 않는다`() = runTest {
        val fixture = AlarmEditorFixture(backgroundScope)
        fixture.navigation.startEditing(SavedAlarm)
        val previousParent = AppRoute.EditAlarm(SavedAlarm.id)

        fixture.navigation.onDestinationClick(previousParent, AppScreen.AlarmRinging)
        fixture.navigation.onAlarmSoundClick(previousParent, AppScreen.AlarmRinging)

        assertEquals(previousParent, fixture.state.backStack.last())
        val nextAlarm = SavedAlarm.copy(id = "another-alarm")
        fixture.navigation.startEditing(nextAlarm)
        fixture.navigation.onDestinationClick(previousParent, AppScreen.EditAlarm)
        fixture.navigation.onAlarmSoundClick(previousParent, AppScreen.EditAlarm)
        fixture.navigation.onBack(previousParent, AppScreen.EditAlarm)

        assertEquals(AppRoute.EditAlarm(nextAlarm.id), fixture.state.backStack.last())
        assertEquals(nextAlarm.id, fixture.alarmSetup.uiState.alarmId)
    }
}

private class AlarmEditorFixture(
    scope: CoroutineScope,
    val state: AppNavigationState = AppNavigationState(),
) {
    val alarmSetup = AlarmSetupViewModel(createAlarmId = { "new-alarm" })
    val repository = EditorDestinationRepository()
    val destination = DestinationViewModel(
        repository = repository,
        productAnalyticsRecorder = DefaultProductAnalyticsRecorder(
            tracker = AnalyticsTracker {},
            usageStore = ProductAnalyticsUsageStore { null },
        ),
        coroutineScope = scope,
    )
    val navigation = AlarmEditorNavigation(state, alarmSetup, destination)

    fun openDestination(): AppRoute.Destination {
        navigation.onDestinationClick(requireNotNull(state.editorRoute), state.requestedScreen)
        return state.backStack.last() as AppRoute.Destination
    }

    fun saveDestination(route: AppRoute.Destination) {
        destination.save(
            destination = SelectedDestination.copy(id = 0L),
            requestId = route.requestId,
            loginState = AnalyticsLoginState.LoggedOut,
        )
    }
}

private class EditorDestinationRepository : DestinationRepository {
    val saveRequests = mutableListOf<SavedDestination>()
    var saveGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<List<SavedDestination>> = flowOf(emptyList())

    override suspend fun fetchAll(): List<SavedDestination> = emptyList()

    override suspend fun sync(): List<SavedDestination> = emptyList()

    override suspend fun save(destination: SavedDestination): SavedDestination {
        saveRequests += destination
        saveGate?.await()
        return SelectedDestination
    }

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private fun restoredState(vararg routes: AppRoute): AppNavigationState {
    val serializer = NavBackStackSerializer(AppRoute.serializer())
    val saved = Json.encodeToString(serializer, NavBackStack(*routes))
    return AppNavigationState(Json.decodeFromString(serializer, saved))
}

private val OriginalDestination = SavedDestination(
    id = 3L,
    name = "회사",
    address = "서울특별시 강남구 테헤란로 1",
    latitude = 37.4979,
    longitude = 127.0276,
)

private val SelectedDestination = OriginalDestination.copy(id = 9L, name = "도서관")

private val SelectedSound = AlarmSoundSelection(name = "새소리", uri = "content://alarm/birds")

private val SavedAlarm = AlarmScheduleRequest(
    id = "alarm-1",
    time = "08:30",
    selectedDays = listOf("월", "수"),
    repeatEnabled = true,
    limitMinutes = 17,
    destinationName = OriginalDestination.name,
    destinationAddress = OriginalDestination.address,
    destinationLatitude = OriginalDestination.latitude,
    destinationLongitude = OriginalDestination.longitude,
    alarmSoundName = "파도",
    alarmSoundUri = "content://alarm/wave",
)
