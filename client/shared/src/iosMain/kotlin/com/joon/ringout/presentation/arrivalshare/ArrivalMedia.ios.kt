@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.joon.ringout.presentation.arrivalshare

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.uikit.LocalUIViewController
import com.joon.ringout.presentation.profilechange.decodePreviewImage
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import platform.Foundation.*
import platform.AVFoundation.*
import platform.UIKit.*
import platform.Photos.*
import platform.PhotosUI.*
import platform.CoreGraphics.*
import platform.darwin.NSObject

private fun ByteArray.data(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
private fun NSData.bytesArray(): ByteArray = checkNotNull(bytes).readBytes(length.toInt())
internal actual suspend fun encodeArrivalPng(image: ImageBitmap): ByteArray = withContext(Dispatchers.Default) {
    check(image.width == 1080 && image.height == 1080)
    checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
}
internal actual suspend fun loadArrivalPhoto(path: String): ImageBitmap = withContext(Dispatchers.Default) {
    Image.makeFromEncoded(checkNotNull(NSData.dataWithContentsOfFile(path)).bytesArray()).toComposeImageBitmap()
}
private fun savePrivatePhoto(image: ImageBitmap): String {
    val directory = NSHomeDirectory() + "/Library/Application Support/arrival-media"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    val path = "$directory/${NSUUID().UUIDString}.png"
    val data = checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes.data()
    check(data.writeToFile(path, true))
    return path
}
private class MediaSession {
    var controller: UIViewController? = null
    var delegate: NSObject? = null
    var active = true
}
@Composable internal actual fun rememberArrivalMedia(onResult: (ArrivalMediaResult) -> Unit): ArrivalMediaActions {
    val host = LocalUIViewController.current
    val callback by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val session = remember { MediaSession() }
    fun finish(result: ArrivalMediaResult) { if (session.active) callback(result) }
    fun fail() = finish(ArrivalMediaResult.Failed("사진 처리에 실패했어요. 권한을 확인하고 다시 시도해 주세요."))
    fun present(controller: UIViewController, delegate: NSObject? = null) {
        check(host.presentedViewController == null)
        session.controller = controller; session.delegate = delegate
        host.presentViewController(controller, true, null)
    }
    fun dismiss(after: () -> Unit) {
        session.controller?.dismissViewControllerAnimated(true) {
            session.controller = null; session.delegate = null; after()
        }
    }
    DisposableEffect(session) {
        session.active = true
        onDispose {
            session.active = false
            session.controller?.dismissViewControllerAnimated(false, null)
            session.controller = null; session.delegate = null
            callback(ArrivalMediaResult.Done())
        }
    }
    fun launchCamera() {
        try {
            check(UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera))
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            }
            val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) { dismiss { finish(ArrivalMediaResult.Done()) } }
                override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                    val selected = runCatching {
                        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as UIImage
                        val size = image.size.useContents { width to height }
                        val ratio = minOf(1.0, 2160.0 / maxOf(size.first, size.second))
                        val target = CGSizeMake(size.first * ratio, size.second * ratio)
                        val renderer = UIGraphicsImageRenderer(target, UIGraphicsImageRendererFormat().apply { scale = 1.0 })
                        val normalized = renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, size.first * ratio, size.second * ratio)) }
                        val bytes = checkNotNull(UIImagePNGRepresentation(normalized)).bytesArray()
                        ArrivalMediaResult.Photo(savePrivatePhoto(Image.makeFromEncoded(bytes).toComposeImageBitmap()))
                    }.getOrElse { ArrivalMediaResult.Failed("촬영한 사진을 처리하지 못했어요. 다시 시도해 주세요.") }
                    dismiss { finish(selected) }
                }
            }
            picker.delegate = delegate
            present(picker, delegate)
        } catch (_: Exception) { finish(ArrivalMediaResult.Failed("카메라를 사용할 수 없어요. 설정에서 카메라 권한을 확인해 주세요.")) }
    }
    return ArrivalMediaActions(
        gallery = {
            try {
                val configuration = PHPickerConfiguration().apply { filter = PHPickerFilter.imagesFilter(); selectionLimit = 1 }
                val picker = PHPickerViewController(configuration)
                val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                        val result = didFinishPicking.firstOrNull() as? PHPickerResult
                        if (result == null) { dismiss { finish(ArrivalMediaResult.Done()) }; return }
                        result.itemProvider.loadFileRepresentationForTypeIdentifier("public.image") { url, _ ->
                            val selected = runCatching { ArrivalMediaResult.Photo(savePrivatePhoto(decodePreviewImage(checkNotNull(url), 2160))) }
                                .getOrElse { ArrivalMediaResult.Failed("사진을 불러오지 못했어요. 다시 선택해 주세요.") }
                            scope.launch { dismiss { finish(selected) } }
                        }
                    }
                }
                picker.setDelegate(delegate)
                // Keep cancellation explicit so every presentation completes exactly once.
                picker.modalInPresentation = true
                present(picker, delegate)
            } catch (_: Exception) { fail() }
        },
        camera = {
            platform.AVFoundation.AVCaptureDevice.requestAccessForMediaType(platform.AVFoundation.AVMediaTypeVideo) { granted ->
                scope.launch {
                    if (granted) launchCamera()
                    else finish(ArrivalMediaResult.Failed("카메라 권한이 필요해요. 설정에서 권한을 허용한 뒤 다시 시도해 주세요."))
                }
            }
        },
        save = { bytes ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) {
                    PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                        PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(PHAssetResourceTypePhoto, bytes.data(), null)
                    }) { success, _ -> scope.launch {
                        if (success) finish(ArrivalMediaResult.Done("사진을 저장했어요.")) else fail()
                    } }
                } else scope.launch { finish(ArrivalMediaResult.Failed("사진 저장 권한이 필요해요. 설정에서 권한을 허용한 뒤 다시 시도해 주세요.")) }
            }
        },
        share = { bytes ->
            try {
                val path = NSTemporaryDirectory() + "Ringout-${NSUUID().UUIDString}.png"
                check(bytes.data().writeToFile(path, true))
                val url = NSURL.fileURLWithPath(path)
                val controller = UIActivityViewController(listOf(url), null)
                controller.completionWithItemsHandler = { _, _, _, error ->
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    session.controller = null; session.delegate = null
                    if (error != null) fail() else finish(ArrivalMediaResult.Done())
                }
                controller.popoverPresentationController?.sourceView = host.view
                controller.popoverPresentationController?.sourceRect = CGRectMake(0.0, 0.0, 1.0, 1.0)
                present(controller)
            } catch (_: Exception) { fail() }
        },
    )
}

@Composable internal actual fun rememberArrivalDraftStore(): ArrivalDraftStore = remember {
    object : ArrivalDraftStore {
        override fun read(key: String) = NSUserDefaults.standardUserDefaults.stringForKey("arrival-share-$key")
        override fun write(key: String, value: String) { NSUserDefaults.standardUserDefaults.setObject(value, "arrival-share-$key") }
    }
}
