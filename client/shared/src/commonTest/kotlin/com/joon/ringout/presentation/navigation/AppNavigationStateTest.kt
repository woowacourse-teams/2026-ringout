package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.domain.push.RoomActivityPushTarget
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppNavigationStateTest {
    @Test
    fun `기록 공유에서 상세와 기록으로 돌아가고 동일 경로 진입은 중복되지 않는다`() {
        val state = AppNavigationState()
        val detail = AppRoute.RecordsDetail("2026-09-30")
        val share = AppRoute.RecordShare("2026-09-30", "group", "history:42")

        state.navigate(AppRoute.Records)
        state.navigate(detail)
        state.navigate(share)
        state.navigate(share.copy())
        assertEquals(listOf(AppRoute.Home, AppRoute.Records, detail, share), state.backStack.toList())
        state.popBackStack(share)
        assertEquals(detail, state.requestedRoute)
        state.popBackStack(detail)
        assertEquals(AppRoute.Records, state.requestedRoute)
    }

    @Test
    fun `잘못된 기록 상세와 공유 경로는 기존 백스택을 변경하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Records)
        state.navigate(AppRoute.RecordsDetail("2026-02-30"))
        state.navigate(AppRoute.RecordShare("2026-09-30", "", "success"))
        assertEquals(listOf(AppRoute.Home, AppRoute.Records), state.backStack.toList())
    }

    @Test
    fun `기록 공유 백스택을 복원하면 기록 식별자와 뒤로 가기 순서를 유지한다`() {
        val detail = AppRoute.RecordsDetail("2026-09-30")
        val share = AppRoute.RecordShare("2026-09-30", "group/한글", "history:42")
        val original = NavBackStack<AppRoute>(AppRoute.Home, AppRoute.Records, detail, share)
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val state = AppNavigationState(Json.decodeFromString(serializer, Json.encodeToString(serializer, original)))

        assertEquals(share, state.requestedRoute)
        state.popBackStack(share)
        assertEquals(detail, state.requestedRoute)
    }

    @Test
    fun `숲 중복 진입은 하나만 쌓고 뒤로 가면 홈으로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Forest)
        state.navigate(AppRoute.Forest)
        assertEquals(listOf(AppRoute.Home, AppRoute.Forest), state.backStack.toList())
        state.popBackStack(AppRoute.Forest)
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `도착 화면은 이전 편집 스택을 정리하고 같은 결과를 중복 추가하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.AddAlarm)
        state.navigate(AppRoute.Destination(4))

        val first = AppRoute.Arrival("arrival-1")
        state.navigate(first)
        state.navigate(first.copy())
        assertEquals(listOf(AppRoute.Home, first), state.backStack.toList())
        assertSame(first, state.requestedRoute)
        assertEquals(listOf(AppRoute.Home, first), state.routesForDisplayedRoute(first))

        val latest = AppRoute.Arrival("arrival-2")
        state.navigate(latest)
        assertEquals(listOf(AppRoute.Home, latest), state.backStack.toList())
        state.navigate(AppRoute.Home)
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `메인 백스택은 인증 경로를 직접 열지 않는다`() {
        val state = AppNavigationState()

        listOf(AppRoute.Login, AppRoute.TermsAgreement).forEach { route ->
            assertFailsWith<IllegalStateException> { state.navigate(route) }
        }

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `인증 백스택에서는 로그인과 약관만 이동하고 루트 로그인은 제거하지 않는다`() {
        val state = AppNavigationState(
            routes = NavBackStack(AppRoute.Login),
            authenticationOnlyMode = true,
        )

        state.navigate(AppRoute.TermsAgreement)
        assertEquals(
            listOf(AppRoute.Login, AppRoute.TermsAgreement),
            state.backStack.toList(),
        )

        state.popBackStack(AppRoute.TermsAgreement)
        assertEquals(listOf(AppRoute.Login), state.backStack.toList())
        state.popBackStack(AppRoute.Login)
        assertEquals(listOf(AppRoute.Login), state.backStack.toList())
    }

    @Test
    fun `비로그인 모드에서 미지원 닉네임 화면 진입은 마이페이지로 이동한다`() {
        listOf(
            AppRoute.NicknameChange,
        ).forEach { accountRoute ->
            val state = AppNavigationState(guestOnlyMode = true)

            state.navigate(accountRoute)

            assertEquals(
                listOf(AppRoute.Home, AppRoute.MyPage),
                state.backStack.toList(),
            )
            assertEquals(AppRoute.MyPage, state.requestedRoute)
        }
    }

    @Test
    fun `복원된 미지원 닉네임 화면은 마이페이지로 이동한다`() {
        listOf(
            AppRoute.NicknameChange,
        ).forEach { accountRoute ->
            val state = AppNavigationState()
            state.navigate(accountRoute)

            state.normalizeForGuestMode()

            assertEquals(
                listOf(AppRoute.Home, AppRoute.MyPage),
                state.backStack.toList(),
            )
            assertEquals(AppRoute.MyPage, state.requestedRoute)
        }
    }

    @Test
    fun `홈에서 마이페이지와 닉네임으로 이동하고 역순으로 돌아온다`() {
        val state = AppNavigationState()

        state.navigate(AppRoute.MyPage)
        assertEquals(AppRoute.MyPage, state.requestedRoute)
        state.navigate(AppRoute.NicknameChange)
        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange),
            state.backStack.toList(),
        )
        assertEquals(AppRoute.NicknameChange, state.requestedRoute)

        state.popBackStack()
        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
        state.popBackStack()
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        assertEquals(AppRoute.Home, state.requestedRoute)
    }

    @Test
    fun `같은 화면을 반복해서 요청해도 중복 경로를 추가하지 않는다`() {
        val state = AppNavigationState()

        repeat(2) { state.navigate(AppRoute.MyPage) }
        repeat(2) { state.navigate(AppRoute.NicknameChange) }

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange),
            state.backStack.toList(),
        )
    }

    @Test
    fun `모임 수정 화면은 현재 호출부 위에 쌓고 뒤로 가면 같은 상세로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomDetail("room-1"))
        val callerStack = state.backStack.toList()
        val editRoute = AppRoute.RoomEdit("room-1")

        state.navigate(editRoute)
        state.navigate(editRoute.copy())

        assertEquals(callerStack + editRoute, state.backStack.toList())
        assertEquals(editRoute, state.requestedRoute)
        state.popBackStack(editRoute)
        assertEquals(callerStack, state.backStack.toList())
        assertEquals(AppRoute.RoomDetail("room-1"), state.requestedRoute)
    }

    @Test
    fun `다른 모임 수정으로 전환하면 호출부를 유지하고 편집 경로를 교체한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomDetail("room-1"))
        val callerStack = state.backStack.toList()

        state.navigate(AppRoute.RoomEdit("room-1"))
        state.navigate(AppRoute.RoomEdit("room-2"))

        assertEquals(callerStack + AppRoute.RoomEdit("room-2"), state.backStack.toList())
    }

    @Test
    fun `다른 회원 관리로 전환하면 호출부를 유지하고 회원 관리 경로를 교체한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomHome("room-1"))
        val callerStack = state.backStack.toList()

        state.navigate(AppRoute.RoomMemberManagement("room-1"))
        state.navigate(AppRoute.RoomMemberManagement("room-2"))

        assertEquals(callerStack + AppRoute.RoomMemberManagement("room-2"), state.backStack.toList())
    }

    @Test
    fun `RoomHome에서 활동 경로를 한 번만 쌓고 종료 후 같은 화면으로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        val roomHome = AppRoute.RoomHome("41")
        state.navigate(roomHome)
        val activity = AppRoute.RoomActivity("41", "2026-10-07")

        state.navigate(activity)
        state.navigate(activity.copy())

        assertEquals(listOf(AppRoute.Home, AppRoute.Social, roomHome, activity), state.backStack.toList())
        assertEquals(listOf(roomHome, activity), state.retainedRoutes(activity).takeLast(2))
        assertEquals(listOf(AppRoute.Home, AppRoute.Social, roomHome, activity), state.routesForDisplayedRoute(activity))

        state.popBackStack(activity)
        assertEquals(roomHome, state.requestedRoute)
    }

    @Test
    fun `같은 모임의 활동 날짜 변경은 경로를 교체하고 늦거나 잘못된 경로는 무시한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        val roomHome = AppRoute.RoomHome("41")
        state.navigate(roomHome)
        val first = AppRoute.RoomActivity("41", "2026-10-07")
        state.navigate(first)

        val next = AppRoute.RoomActivity("41", "2026-10-08")
        state.navigate(next)
        val currentStack = state.backStack.toList()
        state.navigate(AppRoute.RoomActivity("42", "2026-10-08"))
        state.navigate(AppRoute.RoomActivity("41", "2026-02-30"))
        state.navigate(AppRoute.RoomActivity("0", "2026-10-08"))

        assertEquals(currentStack, state.backStack.toList())
        assertEquals(next, state.requestedRoute)
    }

    @Test
    fun `RoomHome이 최상위가 아니면 활동 화면 진입을 무시한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomHome("41"))
        state.navigate(AppRoute.RoomMemberManagement("41"))

        state.navigate(AppRoute.RoomActivity("41", "2026-10-07"))

        assertEquals(AppRoute.RoomMemberManagement("41"), state.requestedRoute)
    }

    @Test
    fun `푸시 클릭은 정확한 활동 백스택을 만들고 뒤로 가면 모임 목록과 홈으로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.MyPage)
        state.navigate(AppRoute.Social)
        val target = requireNotNull(
            RoomActivityPushTarget.parse(
                mapOf("roomId" to "41", "activityDate" to "2026-10-07"),
            ),
        )

        assertTrue(state.openRoomActivityFromPush(target))
        val roomHome = AppRoute.RoomHome("41")
        val activity = AppRoute.RoomActivity("41", "2026-10-07")
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Social, roomHome, activity),
            state.backStack.toList(),
        )

        state.popBackStack(activity)
        assertEquals(roomHome, state.requestedRoute)
        state.popBackStack(roomHome)
        assertEquals(AppRoute.Social, state.requestedRoute)
        state.popBackStack(AppRoute.Social)
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `푸시 클릭은 기존 모임 스택을 대상 모임으로 바꾸고 같은 스택은 중복하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomHome("12"))
        state.navigate(AppRoute.RoomActivity("12", "2026-10-06"))
        val target = requireNotNull(
            RoomActivityPushTarget.parse(
                mapOf("roomId" to "41", "activityDate" to "2026-10-07"),
            ),
        )

        assertTrue(state.openRoomActivityFromPush(target))
        val expected = listOf(
            AppRoute.Home,
            AppRoute.Social,
            AppRoute.RoomHome("41"),
            AppRoute.RoomActivity("41", "2026-10-07"),
        )
        assertEquals(expected, state.backStack.toList())

        assertTrue(state.openRoomActivityFromPush(target))
        assertEquals(expected, state.backStack.toList())
    }

    @Test
    fun `식별자 경로는 전체 값 기준 singleTop으로 기존 항목을 유지한다`() {
        val state = AppNavigationState()
        val firstEditor = AppRoute.EditAlarm("alarm-1")

        state.navigate(firstEditor)
        state.navigate(firstEditor.copy())

        assertSame(firstEditor, state.backStack.last())
        assertEquals(listOf(AppRoute.Home, firstEditor), state.backStack.toList())

        val nextEditor = AppRoute.EditAlarm("alarm-2")
        state.navigate(nextEditor)

        assertSame(nextEditor, state.backStack.last())
        assertEquals(listOf(AppRoute.Home, nextEditor), state.backStack.toList())

        val firstDestination = AppRoute.Destination(1L)
        state.navigate(firstDestination)
        state.navigate(firstDestination.copy())

        assertSame(firstDestination, state.backStack.last())
        assertEquals(
            listOf(AppRoute.Home, nextEditor, firstDestination),
            state.backStack.toList(),
        )

        val nextDestination = AppRoute.Destination(2L)
        state.navigate(nextDestination)

        assertSame(nextDestination, state.backStack.last())
        assertEquals(
            listOf(AppRoute.Home, nextEditor, nextDestination),
            state.backStack.toList(),
        )
    }

    @Test
    fun `활성 미션은 기존 백스택 위에 쌓고 같은 회차는 유지하며 새 회차로 교체한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        val underlyingStack = state.backStack.toList()
        val firstMission = AppRoute.ActiveAlarmTracking("occurrence-1")

        state.navigate(firstMission)
        state.navigate(firstMission.copy())

        assertSame(firstMission, state.backStack.last())
        assertEquals(underlyingStack + firstMission, state.backStack.toList())
        assertEquals(underlyingStack + firstMission, state.retainedRoutes(firstMission))
        assertEquals(listOf(firstMission), state.routesForDisplayedRoute(firstMission))
        assertTrue(state.isCurrentRoute(firstMission))

        val nextMission = AppRoute.ActiveAlarmTracking("occurrence-2")
        state.navigate(nextMission)

        assertSame(nextMission, state.backStack.last())
        assertEquals(underlyingStack + nextMission, state.backStack.toList())

        state.popBackStack(nextMission)

        assertEquals(underlyingStack, state.backStack.toList())
        assertEquals(AppRoute.NicknameChange, state.requestedRoute)
    }

    @Test
    fun `이미 나간 화면의 뒤로 가기는 현재 화면을 제거하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)

        state.popBackStack(AppRoute.MyPage)
        assertEquals(AppRoute.NicknameChange, state.requestedRoute)
        repeat(2) { state.popBackStack(AppRoute.NicknameChange) }

        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
    }

    @Test
    fun `홈에서는 뒤로 가도 백스택이 비지 않는다`() {
        val state = AppNavigationState()

        state.popBackStack()

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `생성 성공은 RoomHome을 열고 뒤로 가면 Social로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)
        state.navigate(AppRoute.RoomCreate)

        assertTrue(state.navigateToRoomHomeFrom(AppRoute.RoomCreate, "41"))
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Social, AppRoute.RoomHome("41")),
            state.backStack.toList(),
        )

        state.popBackStack(AppRoute.RoomHome("41"))

        assertEquals(listOf(AppRoute.Home, AppRoute.Social), state.backStack.toList())
        assertEquals(AppRoute.Social, state.requestedRoute)
    }

    @Test
    fun `가입 성공은 상세 경로를 RoomHome으로 바꾸고 이전 상세 콜백은 무시한다`() {
        val state = AppNavigationState()
        val roomDetail = AppRoute.RoomDetail("7")
        state.navigate(roomDetail)

        assertTrue(state.navigateToRoomHomeFrom(roomDetail, "7"))
        assertFalse(state.navigateToRoomHomeFrom(roomDetail, "7"))
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Social, AppRoute.RoomHome("7")),
            state.backStack.toList(),
        )
    }

    @Test
    fun `가입 모임 바로가기는 상세 경유 없이 RoomHome으로 이동하고 뒤로 가면 Social로 돌아온다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.Social)

        state.navigate(AppRoute.RoomHome("7"))

        assertEquals(
            listOf(AppRoute.Home, AppRoute.Social, AppRoute.RoomHome("7")),
            state.backStack.toList(),
        )
        state.popBackStack(AppRoute.RoomHome("7"))
        assertEquals(AppRoute.Social, state.requestedRoute)
    }

    @Test
    fun `복원된 RoomHome 경로는 모임 식별자를 유지한다`() {
        val roomHome = AppRoute.RoomHome("room-41")
        val backStack = NavBackStack<AppRoute>(AppRoute.Home, AppRoute.Social, roomHome)
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, backStack))

        assertEquals(backStack.toList(), restored.toList())
        assertEquals(roomHome, restored.last())
    }

    @Test
    fun `복원한 모임 상세 경로가 같은 모임 식별자를 유지한다`() {
        val roomDetail = AppRoute.RoomDetail("room-42")
        val backStack = NavBackStack<AppRoute>(AppRoute.Home, AppRoute.Social, roomDetail)
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, backStack))
        val state = AppNavigationState(restored)

        assertEquals(backStack.toList(), state.backStack.toList())
        assertEquals(roomDetail, state.requestedRoute)
    }

    @Test
    fun `활성 미션에서 홈으로 이동하면 이전 경로를 제거한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        state.navigate(AppRoute.ActiveAlarmTracking("occurrence-1"))

        state.navigate(AppRoute.Home)

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        assertEquals(AppRoute.Home, state.requestedRoute)
    }

    @Test
    fun `직렬화한 활성 미션 백스택을 식별자와 함께 복원한다`() {
        val activeMission = AppRoute.ActiveAlarmTracking("occurrence-1")
        val backStack = NavBackStack<AppRoute>(
            AppRoute.Home,
            AppRoute.MyPage,
            activeMission,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val saved = Json.encodeToString(serializer, backStack)
        val restored = Json.decodeFromString(serializer, saved)
        val state = AppNavigationState(restored)

        assertEquals(backStack.toList(), state.backStack.toList())
        assertEquals(activeMission, state.requestedRoute)
        assertTrue(state.isCurrentRoute(activeMission))

        state.popBackStack(activeMission)

        assertEquals(backStack.dropLast(1), state.backStack.toList())
        assertEquals(AppRoute.MyPage, state.requestedRoute)
        assertTrue(state.isCurrentRoute(AppRoute.MyPage))
    }

    @Test
    fun `현재 백스택 최상단만 표시 경로로 사용한다`() {
        val state = AppNavigationState(
            routes = NavBackStack(AppRoute.Login),
            authenticationOnlyMode = true,
        )
        state.navigate(AppRoute.TermsAgreement)
        val expectedStack = state.backStack.toList()

        assertNull(state.routesForDisplayedRoute(AppRoute.Login))
        assertNull(state.routesForDisplayedRoute(AppRoute.MyPage))
        assertEquals(expectedStack, state.routesForDisplayedRoute(AppRoute.TermsAgreement)?.toList())
        assertEquals(expectedStack, state.backStack.toList())
        assertTrue(state.isCurrentRoute(AppRoute.TermsAgreement))
        assertFalse(state.isCurrentRoute(AppRoute.Login))
    }

    @Test
    fun `알람 울림은 실제 백스택을 바꾸지 않고 단일 표시 경로로 유지한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        val underlyingStack = state.backStack.toList()
        val ringingRoute = AppRoute.AlarmRinging("alarm-1")

        assertEquals(listOf(ringingRoute), state.routesForDisplayedRoute(ringingRoute))
        assertEquals(underlyingStack + ringingRoute, state.retainedRoutes(ringingRoute))
        assertEquals(underlyingStack, state.backStack.toList())
        assertEquals(AppRoute.NicknameChange, state.requestedRoute)
    }

    @Test
    fun `백스택에 없는 편집 경로는 초안과 부모 없이 표시 경로로 합성하지 않는다`() {
        val state = AppNavigationState()

        for (
            displayedRoute in listOf(
                AppRoute.EditAlarm("alarm-1"),
                AppRoute.Destination(1L),
                AppRoute.AlarmSound,
                AppRoute.Onboarding,
            )
        ) {
            assertNull(state.routesForDisplayedRoute(displayedRoute))
        }

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `목적지와 알람음에서 뒤로 가면 같은 생성 또는 수정 화면으로 돌아온다`() {
        for (parent in listOf(AppRoute.AddAlarm, AppRoute.EditAlarm("alarm-1"))) {
            val state = AppNavigationState()
            state.navigate(parent)

            for (picker in listOf(AppRoute.Destination(7L), AppRoute.AlarmSound)) {
                state.navigate(picker)

                assertEquals(listOf(AppRoute.Home, parent, picker), state.backStack.toList())
                assertEquals(parent, state.editorRoute)
                state.popBackStack(picker)
                assertEquals(listOf(AppRoute.Home, parent), state.backStack.toList())
            }

            state.popBackStack(parent)
            assertEquals(listOf(AppRoute.Home), state.backStack.toList())
            assertNull(state.editorRoute)
        }
    }

    @Test
    fun `선택 화면을 반복하거나 바꿔도 편집 부모 아래에는 현재 자식만 남긴다`() {
        val state = AppNavigationState()
        val parent = AppRoute.EditAlarm("alarm-1")
        state.navigate(parent)

        repeat(2) { state.navigate(AppRoute.Destination(1L)) }
        repeat(2) { state.navigate(AppRoute.AlarmSound) }
        state.navigate(AppRoute.Destination(2L))

        assertEquals(
            listOf(AppRoute.Home, parent, AppRoute.Destination(2L)),
            state.backStack.toList(),
        )
        state.navigate(AppRoute.EditAlarm("alarm-2"))
        assertEquals(listOf(AppRoute.Home, AppRoute.EditAlarm("alarm-2")), state.backStack.toList())
        state.navigate(AppRoute.AddAlarm)
        assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), state.backStack.toList())
    }

    @Test
    fun `편집 부모가 없는 목적지와 알람음 이동은 기존 스택을 바꾸지 않고 거절한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.MyPage)
        val expectedStack = state.backStack.toList()

        for (picker in listOf(AppRoute.Destination(1L), AppRoute.AlarmSound)) {
            assertFailsWith<IllegalArgumentException> { state.navigate(picker) }
        }

        assertEquals(expectedStack, state.backStack.toList())
    }

    @Test
    fun `현재 편집 자식만 표시하고 부모 식별자와 백스택을 보존한다`() {
        val state = AppNavigationState()
        val parent = AppRoute.EditAlarm("alarm-1")
        state.navigate(parent)
        state.navigate(AppRoute.Destination(9L))
        val expectedStack = state.backStack.toList()

        assertNull(state.routesForDisplayedRoute(parent))
        assertEquals(
            expectedStack,
            state.routesForDisplayedRoute(AppRoute.Destination(9L))?.toList(),
        )
        val ringingRoute = AppRoute.AlarmRinging("alarm-1")
        assertEquals(listOf(ringingRoute), state.routesForDisplayedRoute(ringingRoute))
        assertEquals(expectedStack, state.backStack.toList())

        state.navigate(AppRoute.AlarmSound)
        assertEquals(
            listOf(AppRoute.Home, parent, AppRoute.AlarmSound),
            state.routesForDisplayedRoute(AppRoute.AlarmSound)?.toList(),
        )
    }

}
