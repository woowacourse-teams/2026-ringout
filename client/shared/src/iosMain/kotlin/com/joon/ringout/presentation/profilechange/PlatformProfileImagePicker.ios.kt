package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize

@Composable
internal actual fun rememberProfileImagePicker(
    onResult: (ProfileImagePickResult) -> Unit,
): () -> Unit {
    val viewController = LocalUIViewController.current
    val currentOnResult = rememberUpdatedState(onResult)
    val lifetime = remember { ProfileImagePickerLifetime() }
    val session = remember(lifetime) { ProfileImagePickerSession() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifetime, session) {
        lifetime.active = true
        onDispose {
            lifetime.active = false
            session.picker?.dismissViewControllerAnimated(flag = false, completion = null)
            session.picker = null
            session.delegate = null
        }
    }

    return remember(viewController, lifetime, session) {
        {
            runCatching {
                val configuration = PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter()
                    selectionLimit = 1
                }
                val picker = PHPickerViewController(configuration)
                val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                        picker.dismissViewControllerAnimated(flag = true, completion = null)
                        session.picker = null

                        val result = didFinishPicking.firstOrNull() as? PHPickerResult
                        if (result == null) {
                            session.delegate = null
                            if (lifetime.active) currentOnResult.value(ProfileImagePickResult.Cancelled)
                            return
                        }

                        result.itemProvider.loadFileRepresentationForTypeIdentifier("public.image") { fileUrl, _ ->
                            val pickResult = if (fileUrl == null) {
                                ProfileImagePickResult.Failure
                            } else {
                                runCatching {
                                    ProfileImagePickResult.Selected(decodePreviewImage(fileUrl))
                                }.getOrElse { ProfileImagePickResult.Failure }
                            }
                            coroutineScope.launch {
                                if (lifetime.active) currentOnResult.value(pickResult)
                            }
                            session.delegate = null
                        }
                    }
                }
                session.picker = picker
                session.delegate = delegate
                picker.setDelegate(delegate)
                viewController.presentViewController(picker, animated = true, completion = null)
            }.onFailure {
                session.picker = null
                session.delegate = null
                if (lifetime.active) currentOnResult.value(ProfileImagePickResult.Failure)
            }
        }
    }
}

private class ProfileImagePickerLifetime(var active: Boolean = true)

private class ProfileImagePickerSession(
    var picker: PHPickerViewController? = null,
    var delegate: PHPickerViewControllerDelegateProtocol? = null,
)

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun decodePreviewImage(fileUrl: NSURL): ImageBitmap {
    val urlReference = memScoped {
        val path = fileUrl.path ?: error("The selected image could not be opened")
        val cfPath = CFStringCreateWithCString(null, path, kCFStringEncodingUTF8)
            ?: error("The selected image could not be opened")
        try {
            CFURLCreateWithFileSystemPath(null, cfPath, kCFURLPOSIXPathStyle, false)
                ?: error("The selected image could not be opened")
        } finally {
            CFRelease(cfPath)
        }
    }
    val source = try {
        CGImageSourceCreateWithURL(urlReference, null)
            ?: error("The selected image could not be opened")
    } finally {
        CFRelease(urlReference)
    }
    try {
        val options = CFDictionaryCreateMutable(
            null,
            3,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: error("The selected image options could not be prepared")
        val maxDimensionNumber = memScoped {
            val value = alloc<IntVar>()
            value.value = MaxPreviewDimension
            CFNumberCreate(null, kCFNumberIntType, value.ptr)
        } ?: error("The selected image options could not be prepared")
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxDimensionNumber)
        CFRelease(maxDimensionNumber)
        val thumbnail = try {
            CGImageSourceCreateThumbnailAtIndex(source, 0uL, options)
                ?: error("The selected file is not a readable image")
        } finally {
            CFRelease(options)
        }
        try {
            val jpegData = UIImageJPEGRepresentation(UIImage(cGImage = thumbnail), 0.9)
                ?: error("The selected image could not be prepared")
            val byteCount = jpegData.length.toLong()
            check(byteCount in 1..MaxEncodedPreviewBytes) { "The selected image preview is too large" }
            val bytes = jpegData.bytes?.readBytes(byteCount.toInt())
                ?: error("The selected image could not be read")

            return Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } finally {
            CGImageRelease(thumbnail)
        }
    } finally {
        CFRelease(source)
    }
}

private const val MaxPreviewDimension = 768
private const val MaxEncodedPreviewBytes = 16L * 1024 * 1024
