package com.joon.ringout.presentation.arrivalshare
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
internal data class ArrivalMediaActions(val camera: () -> Unit, val gallery: () -> Unit, val save: (ByteArray) -> Unit, val share: (ByteArray) -> Unit)
internal sealed interface ArrivalMediaResult {
    data class Photo(val path: String) : ArrivalMediaResult
    data class Done(val message: String? = null) : ArrivalMediaResult
    data class Failed(val message: String) : ArrivalMediaResult
}
@Composable internal expect fun rememberArrivalMedia(onResult: (ArrivalMediaResult) -> Unit): ArrivalMediaActions
internal expect suspend fun loadArrivalPhoto(path: String): ImageBitmap
internal expect suspend fun encodeArrivalPng(image: ImageBitmap): ByteArray

internal interface ArrivalDraftStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}
@Composable internal expect fun rememberArrivalDraftStore(): ArrivalDraftStore
@kotlinx.serialization.Serializable
internal data class ArrivalDraft(val photoPath: String? = null, val showTime: Boolean = true, val showDate: Boolean = true, val date: String = "", val time: String? = null)
