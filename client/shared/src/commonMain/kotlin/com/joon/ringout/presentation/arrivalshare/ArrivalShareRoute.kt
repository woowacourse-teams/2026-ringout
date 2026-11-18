package com.joon.ringout.presentation.arrivalshare
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.presentation.navigation.NavigationBackAction
import com.joon.ringout.presentation.navigation.LocalNavigationBackActionRegistrar
import com.joon.ringout.presentation.destination.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Composable internal fun ArrivalShareRoute(viewModel: ArrivalShareViewModel, onBack: () -> Unit, mediaOverride: ArrivalMediaActions? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val store = rememberArrivalDraftStore()
    var draftLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(store, viewModel) {
        val draft = runCatching { store.read(viewModel.occurrenceId)?.let { Json.decodeFromString<ArrivalDraft>(it) } }.getOrNull()
        viewModel.restoreDraft(draft)
        draftLoaded = true
    }
    LaunchedEffect(draftLoaded, state.photoPath, state.showDate, state.showTime, state.date, state.time, state.arrivalTime, state.wakeEpochMillis, state.arrivalEpochMillis, state.useBlackText) {
        if (draftLoaded && !state.isLoading && state.error == null) {
            runCatching { store.write(viewModel.occurrenceId, Json.encodeToString(ArrivalDraft(
                photoPath = state.photoPath, showTime = state.showTime, showDate = state.showDate,
                date = state.date, useBlackText = state.useBlackText,
                wakeEpochMillis = state.wakeEpochMillis, wakeLabel = state.time,
                arrivalEpochMillis = state.arrivalEpochMillis, arrivalLabel = state.arrivalTime,
            ))) }
                .onFailure { viewModel.finishMedia(ArrivalMediaResult.Failed("편집 내용을 보관하지 못했어요.")) }
        }
    }
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadedPath by remember { mutableStateOf<String?>(null) }
    var photoFailed by remember { mutableStateOf(false) }
    LaunchedEffect(state.photoPath) {
        photoFailed = false
        try {
            photo = state.photoPath?.let { loadArrivalPhoto(it) }
            loadedPath = state.photoPath
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            photoFailed = true
            viewModel.finishMedia(ArrivalMediaResult.Failed("사진을 불러오지 못했어요. 사진을 다시 선택해 주세요."))
        }
    }
    val platformMedia = rememberArrivalMedia(viewModel::finishMedia)
    val media = mediaOverride ?: platformMedia
    val back = { if (!state.busy) onBack() }
    NavigationBackAction(back)
    if (LocalNavigationBackActionRegistrar.current == null) PlatformBackHandler(onBack = back)
    val ready = draftLoaded && !photoFailed && loadedPath == state.photoPath
    fun export(save: Boolean) {
        if (!ready || !viewModel.beginMedia()) return
        scope.launch {
            try {
                withFrameNanos { }
                val bytes = encodeArrivalPng(layer.toImageBitmap())
                if (save) media.save(bytes) else media.share(bytes)
            } catch (e: CancellationException) { viewModel.finishMedia(ArrivalMediaResult.Done()); throw e
            } catch (_: Exception) { viewModel.finishMedia(ArrivalMediaResult.Failed("이미지를 만들지 못했어요. 다시 시도해 주세요.")) }
        }
    }
    ArrivalShareScreen(state, back, viewModel::setShowTime, viewModel::setShowDate, viewModel::retry,
        photo = photo, mediaReady = ready, onToggleTextColor = viewModel::toggleTextColor,
        cardModifier = Modifier.drawWithContent {
            val factor = 1080f / size.width
            layer.record(size = IntSize(1080, 1080)) {
                scale(factor, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
            }
            scale(1f / factor, pivot = Offset.Zero) { drawLayer(layer) }
        },
        onCamera = { if (viewModel.beginMedia()) media.camera() },
        onGallery = { if (viewModel.beginMedia()) media.gallery() },
        onSave = { export(true) }, onShare = { export(false) },
    )
}
