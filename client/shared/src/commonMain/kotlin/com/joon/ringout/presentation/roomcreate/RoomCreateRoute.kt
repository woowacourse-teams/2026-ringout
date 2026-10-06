package com.joon.ringout.presentation.roomcreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.roomcreate.model.RoomCreateDraft
import com.joon.ringout.presentation.to24HourTimeString
import com.joon.ringout.presentation.roomlist.model.RoomMutationEntryIds
import com.joon.ringout.presentation.roomlist.model.RoomMutationSource
import com.joon.ringout.presentation.roomlist.model.RoomMutationType
import com.joon.ringout.presentation.roomlist.model.RoomMutationUiState

@Composable
internal fun RoomCreateRoute(
    viewModel: RoomCreateViewModel,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onBackClick: () -> Unit,
    mutationState: RoomMutationUiState,
    onMutationSourceVisible: (Long) -> Unit,
    onMutationSourceHidden: (Long) -> Unit,
    onCreateDraft: (RoomMutationSource, RoomCreateInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceEntryId = rememberSaveable(viewModel) { RoomMutationEntryIds.next() }
    val source = RoomMutationSource(entryId = sourceEntryId, type = RoomMutationType.Create)
    LaunchedEffect(source.entryId, authSessionState, sessionIdentity) {
        onMutationSourceVisible(source.entryId)
    }
    DisposableEffect(source.entryId) {
        onDispose { onMutationSourceHidden(source.entryId) }
    }
    LaunchedEffect(viewModel) {
        viewModel.initializeTimeIfNeeded(currentLocalClockSnapshot().to24HourTimeString())
    }

    val isCurrentMutation = mutationState.source == source

    RoomCreateScreen(
        uiState = viewModel.uiState,
        onBackClick = { viewModel.onBack(onBackClick) },
        onNameChange = viewModel::updateName,
        onIntroductionChange = viewModel::updateIntroduction,
        onDayClick = viewModel::toggleDay,
        onAmPmChange = viewModel::updateAmPm,
        onHourChange = viewModel::updateHour,
        onMinuteChange = viewModel::updateMinute,
        onAction = {
            if (viewModel.uiState.step < 3) {
                viewModel.goToNextStep()
            } else {
                viewModel.createDraft()?.let { draft ->
                    if (draft.introduction.length > RoomIntroductionServerMaxLength) {
                        viewModel.setSubmitError(RoomIntroductionServerLimitMessage)
                    } else {
                        viewModel.clearSubmitError()
                        onCreateDraft(source, draft.toDomainInput())
                    }
                }
            }
        },
        isMutationInProgress = mutationState.isInProgress,
        mutationErrorMessage = mutationState.errorMessage.takeIf { isCurrentMutation },
        modifier = modifier,
    )
}

private fun RoomCreateDraft.toDomainInput() = RoomCreateInput(
    name = name,
    description = introduction,
    activityDays = selectedDays.map { day ->
        checkNotNull(ServerWeekdayNames[day]) { "지원하지 않는 활동 요일입니다: $day" }
    }.distinct(),
    activityTime = time24Hour,
)

private val ServerWeekdayNames = mapOf(
    "월" to "MONDAY",
    "화" to "TUESDAY",
    "수" to "WEDNESDAY",
    "목" to "THURSDAY",
    "금" to "FRIDAY",
    "토" to "SATURDAY",
    "일" to "SUNDAY",
)

private const val RoomIntroductionServerMaxLength = 300
private const val RoomIntroductionServerLimitMessage = "모임 소개는 서버 기준 300자 이내로 작성해 주세요."
