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
import com.joon.ringout.domain.room.RoomImageUpload
import com.joon.ringout.presentation.profilechange.ProfileImagePickResult
import com.joon.ringout.presentation.profilechange.rememberProfileImagePicker
import com.joon.ringout.presentation.profilechange.component.profileimage.ProfileImageSizeLimitDialog
import com.joon.ringout.presentation.roomedit.component.RoomEditUnavailableScreen

@Composable
internal fun RoomEditRoute(
    roomId: String,
    viewModel: RoomEditViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    var selectedImage by remember(roomId) { mutableStateOf<SelectedRoomEditImage?>(null) }
    var selectedImageToken by remember(roomId) { mutableStateOf<Long?>(null) }
    var nextImageSelectionToken by remember(roomId) { mutableStateOf(0L) }
    var imageError by remember(roomId) { mutableStateOf<String?>(null) }
    var showImageSizeLimitDialog by remember(roomId) { mutableStateOf(false) }
    val routeIsActive = remember(viewModel, roomId) { mutableStateOf(true) }

    DisposableEffect(routeIsActive) {
        routeIsActive.value = true
        onDispose { routeIsActive.value = false }
    }

    LaunchedEffect(viewModel, roomId) {
        viewModel.onRouteVisible(roomId)
    }
    LaunchedEffect(viewModel, uiState.imageSelectionToken, selectedImageToken) {
        val selectionToken = uiState.imageSelectionToken
        if (selectionToken == null) {
            selectedImage = null
            selectedImageToken = null
            return@LaunchedEffect
        }
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
                val upload = RoomImageUpload(
                    bytes = result.upload.bytes,
                    contentType = result.upload.contentType,
                    fileName = result.upload.fileName,
                )
                selectedImage = SelectedRoomEditImage(result.image, upload)
                selectedImageToken = token
                viewModel.onImageSelected(token, upload)
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
            isLoading = uiState.isLoading,
            message = uiState.loadErrorMessage,
            canRetry = uiState.canRetryLoad,
            isBackEnabled = !uiState.isSaving,
            onBackClick = if (uiState.isSaving) ({}) else onBackClick,
            onRetry = viewModel::onRetry,
            modifier = modifier,
        )
    } else {
        val previewMatchesSelection = uiState.imageSelectionToken?.let { token ->
            token == selectedImageToken && selectedImage?.upload != null
        } ?: true

        RoomEditScreen(
            uiState = uiState,
            selectedImage = selectedImage?.image,
            isImagePreviewAvailable = previewMatchesSelection,
            imageError = imageError,
            onBackClick = if (uiState.isSaving) ({}) else onBackClick,
            onNameChange = viewModel::updateName,
            onIntroductionChange = viewModel::updateIntroduction,
            onImageChangeClick = launchImagePicker,
            onSaveClick = {
                if (!uiState.canSave || !previewMatchesSelection) return@RoomEditScreen
                viewModel.saveChanges()
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

private data class SelectedRoomEditImage(
    val image: ImageBitmap,
    val upload: RoomImageUpload,
)
