package com.joon.ringout.presentation.arrivalshare

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID

internal actual suspend fun loadArrivalPhoto(path: String): ImageBitmap = withContext(Dispatchers.IO) {
    checkNotNull(BitmapFactory.decodeFile(path)).asImageBitmap()
}
internal actual suspend fun encodeArrivalPng(image: ImageBitmap): ByteArray = withContext(Dispatchers.Default) {
    check(image.width == 1080 && image.height == 1080)
    ByteArrayOutputStream().use { output -> check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray() }
}
internal fun importArrivalPhoto(context: Context, uri: Uri): String {
    val directory = File(context.filesDir, "arrival-media").apply { mkdirs() }
    val source = File(directory, "source-${UUID.randomUUID()}")
    try {
        context.contentResolver.openInputStream(uri)!!.use { input -> source.outputStream().use { input.copyTo(it) } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2160) sample *= 2
        val original = checkNotNull(BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val exif = ExifInterface(source)
        val transform = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(exif.rotationDegrees.toFloat()) }
        val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, transform, true)
        try {
            val output = File(directory, "photo-${UUID.randomUUID()}.png")
            output.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return output.path
        } finally { if (oriented !== original) oriented.recycle(); original.recycle() }
    } finally { source.delete() }
}
internal fun saveArrivalPng(context: Context, bytes: ByteArray) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Ringout-${UUID.randomUUID()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Ringout")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= 29) {
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri, published, null, null) > 0)
        }
    } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
}
@Composable internal actual fun rememberArrivalMedia(onResult: (ArrivalMediaResult) -> Unit): ArrivalMediaActions {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    fun failure() = callback(ArrivalMediaResult.Failed("사진 처리에 실패했어요. 권한을 확인하고 다시 시도해 주세요."))
    fun import(uri: Uri?) {
        if (uri == null) { callback(ArrivalMediaResult.Done()); return }
        scope.launch {
            try { callback(ArrivalMediaResult.Photo(withContext(Dispatchers.IO) { importArrivalPhoto(context, uri) })) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure() }
        }
    }
    fun save(bytes: ByteArray) { scope.launch {
        try { withContext(Dispatchers.IO) { saveArrivalPng(context, bytes) }; callback(ArrivalMediaResult.Done("사진을 저장했어요.")) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure() }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bytes = pendingSave; pendingSave = null
        if (granted && bytes != null) save(bytes) else callback(ArrivalMediaResult.Failed("사진 저장 권한이 필요해요. 설정에서 권한을 허용한 뒤 다시 시도해 주세요."))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath; cameraPath = null
        if (!success || path == null) { path?.let { File(it).delete() }; callback(ArrivalMediaResult.Done()) }
        else scope.launch {
            try { callback(ArrivalMediaResult.Photo(withContext(Dispatchers.IO) { importArrivalPhoto(context, Uri.fromFile(File(path))) })) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure() }
            finally { File(path).delete() }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it) }
    DisposableEffect(Unit) { onDispose { callback(ArrivalMediaResult.Done()) } }
    return ArrivalMediaActions(
        camera = {
            try {
                val file = File(context.cacheDir, "arrival-media/camera-${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs(); createNewFile() }
                cameraPath = file.path
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.arrival.media", file))
            } catch (_: Exception) { cameraPath?.let { File(it).delete() }; cameraPath = null; failure() }
        },
        gallery = { try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Exception) { failure() } },
        save = { bytes ->
            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingSave = bytes
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else save(bytes)
        },
        share = { bytes -> scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { File(context.cacheDir, "arrival-media/share-${UUID.randomUUID()}.png").apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.arrival.media", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Ringout", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "기상 기록 공유"))
                callback(ArrivalMediaResult.Done())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure() }
        } },
    )
}

@Composable internal actual fun rememberArrivalDraftStore(): ArrivalDraftStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val prefs = context.getSharedPreferences("arrival-share-drafts", Context.MODE_PRIVATE)
        object : ArrivalDraftStore {
            override fun read(key: String) = prefs.getString(key, null)
            override fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        }
    }
}
