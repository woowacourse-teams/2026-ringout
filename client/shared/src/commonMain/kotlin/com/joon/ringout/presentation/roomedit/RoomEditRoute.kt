package com.joon.ringout.presentation.roomedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.joon.ringout.presentation.profilechange.ProfileImagePickResult
import com.joon.ringout.presentation.profilechange.rememberProfileImagePicker
import com.joon.ringout.presentation.profilechange.component.profileimage.ProfileImageSizeLimitDialog
import com.joon.ringout.presentation.roomedit.component.RoomEditUnavailableScreen
import com.joon.ringout.presentation.roomedit.model.RoomEditDraft
import com.joon.ringout.presentation.roomedit.model.RoomEditImageChange
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomEditRoute(
    roomId: String,
    originalRoom: RoomUiModel?,
    isLoading: Boolean,
    loadError: String?,
    viewModel: RoomEditViewModel,
    onRouteVisible: () -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    onDraft: (RoomEditDraft, ImageBitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    var selectedImage by remember(roomId) { mutableStateOf<ImageBitmap?>(null) }
    var selectedImageToken by remember(roomId) { mutableStateOf<Long?>(null) }
    var nextImageSelectionToken by remember(roomId) { mutableStateOf(0L) }
    var imageError by remember(roomId) { mutableStateOf<String?>(null) }
    var showImageSizeLimitDialog by remember(roomId) { mutableStateOf(false) }
    val routeIsActive = remember(viewModel, roomId) { mutableStateOf(true) }

    DisposableEffect(routeIsActive) {
        routeIsActive.value = true
        onDispose { routeIsActive.value = false }
    }

    LaunchedEffect(viewModel, roomId, originalRoom) {
        viewModel.initialize(roomId, originalRoom)
        onRouteVisible()
    }
    LaunchedEffect(viewModel, uiState.imageSelectionToken, selectedImageToken) {
        val selectionToken = uiState.imageSelectionToken ?: return@LaunchedEffect
        if (selectionToken != selectedImageToken) {
            viewModel.onImagePreviewLost(selectionToken)
            selectedImage = null
            selectedImageToken = null
            imageError = "사진을 다시 선택해 주세요."
        }
    }

    val launchImagePicker = rememberProfileImagePicker { result ->
        if (!routeIsActive.value) return@rememberProfileImagePicker
        when (result) {
            is ProfileImagePickResult.Selected -> {
                val token = nextImageSelectionToken + 1L
                nextImageSelectionToken = token
                selectedImage = result.image
                selectedImageToken = token
                viewModel.onImageSelected(token)
                imageError = null
                showImageSizeLimitDialog = false
            }
            ProfileImagePickResult.TooLarge -> {
                imageError = null
                showImageSizeLimitDialog = true
            }
            ProfileImagePickResult.Cancelled -> imageError = null
            ProfileImagePickResult.Failure -> {
                imageError = "사진을 불러오지 못했어요. 다시 선택해 주세요."
            }
        }
    }

    if (!uiState.isOriginalLoaded) {
        RoomEditUnavailableScreen(
            isLoading = isLoading,
            message = loadError,
            onBackClick = onBackClick,
            onRetry = onRetry,
            modifier = modifier,
        )
    } else {
        val previewMatchesSelection = uiState.imageSelectionToken?.let { token ->
            token == selectedImageToken && selectedImage != null
        } ?: true

        RoomEditScreen(
            uiState = uiState,
            selectedImage = selectedImage,
            isImagePreviewAvailable = previewMatchesSelection,
            imageError = imageError,
            onBackClick = onBackClick,
            onNameChange = viewModel::updateName,
            onIntroductionChange = viewModel::updateIntroduction,
            onImageChangeClick = launchImagePicker,
            onSaveClick = {
                if (!uiState.canSave || !previewMatchesSelection) return@RoomEditScreen
                val draft = viewModel.createDraft() ?: return@RoomEditScreen
                val preview = when (val imageChange = draft.imageChange) {
                    RoomEditImageChange.Unchanged -> null
                    is RoomEditImageChange.Replace -> {
                        if (
                            imageChange.selectionToken == selectedImageToken &&
                            selectedImage != null
                        ) {
                            selectedImage
                        } else {
                            viewModel.onImagePreviewLost(imageChange.selectionToken)
                            imageError = "사진을 다시 선택해 주세요."
                            return@RoomEditScreen
                        }
                    }
                }
                onDraft(draft, preview)
            },
            modifier = modifier,
        )
    }

    if (showImageSizeLimitDialog) {
        ProfileImageSizeLimitDialog(
            onConfirm = { showImageSizeLimitDialog = false },
            accessibilityTitle = "모임 대표 이미지 용량 안내",
            confirmLabel = "모임 대표 이미지 용량 안내 확인",
        )
    }
}
