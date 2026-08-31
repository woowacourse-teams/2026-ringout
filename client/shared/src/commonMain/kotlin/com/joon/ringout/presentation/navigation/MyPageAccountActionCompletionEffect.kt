package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.signup.SignupViewModel

@Composable
internal fun MyPageAccountActionCompletionEffect(
    navigationState: AppNavigationState,
    myPageViewModel: MyPageViewModel?,
    signupViewModel: SignupViewModel?,
) {
    val completedAction =
        myPageViewModel?.uiState?.accountAction as? MyPageAccountActionState.Completed
    val handler = remember(navigationState, myPageViewModel, signupViewModel) {
        myPageViewModel?.let {
            MyPageAccountActionCompletionHandler(
                resetSignup = { signupViewModel?.resetSignup() },
                navigate = navigationState::navigate,
                consumeCompletedEvent = myPageViewModel::consumeAccountActionCompletedEvent,
            )
        }
    }
    LaunchedEffect(handler, completedAction?.eventId) {
        val completed = completedAction ?: return@LaunchedEffect
        handler?.handle(completed)
    }
}

internal class MyPageAccountActionCompletionHandler(
    private val resetSignup: () -> Unit,
    private val navigate: (AppRoute) -> Unit,
    private val consumeCompletedEvent: (Long) -> Unit,
) {
    fun handle(completed: MyPageAccountActionState.Completed) {
        resetSignup()
        navigate(completed.action.completionDestination())
        consumeCompletedEvent(completed.eventId)
    }
}
