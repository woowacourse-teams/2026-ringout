package com.joon.ringout.presentation.forest

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun PlatformForestWebView(
    endpoint: ForestEndpoint,
    controller: ForestWebController,
    onMessage: (String) -> Unit,
    onPageStarted: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val message by rememberUpdatedState(onMessage)
    val started by rememberUpdatedState(onPageStarted)
    val error by rememberUpdatedState(onError)
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }
    if (!supported || (endpoint.isLocalHttp && !debug)) {
        LaunchedEffect(Unit) { error(if (!supported) "Android System WebView를 업데이트해 주세요." else "숲 화면의 HTTPS 주소를 설정해 주세요.") }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { hostContext ->
            WebView(hostContext).apply {
                var alive = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                WebViewCompat.addWebMessageListener(this, "RingoutForestNative", setOf(endpoint.origin)) { _, payload, source, mainFrame, _ ->
                    if (alive && mainFrame && source.toString().trimEnd('/') == endpoint.origin && endpoint.allowsDocument(url)) {
                        payload.data?.let(message)
                    }
                }
                controller.evaluate = { script -> if (alive && endpoint.allowsDocument(url)) evaluateJavascript(script, null) }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !request.isForMainFrame || !endpoint.allowsDocument(request.url.toString())
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        if (!endpoint.allowsDocument(url)) { stopLoading(); error("허용되지 않은 숲 화면 주소예요.") }
                        else started()
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                        if (request.isForMainFrame) error("숲 화면에 연결하지 못했어요. 연결 상태를 확인해 주세요.")
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) error("숲 화면을 불러오지 못했어요.")
                    }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        alive = false
                        controller.evaluate = null
                        error("숲 화면이 종료됐어요. 다시 시도해 주세요.")
                        return true
                    }
                }
                loadUrl(endpoint.url)
            }
        },
        onRelease = { view ->
            controller.evaluate = null
            WebViewCompat.removeWebMessageListener(view, "RingoutForestNative")
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.destroy()
        },
    )
}
