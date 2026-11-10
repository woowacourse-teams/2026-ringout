@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.joon.ringout.presentation.forest

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.readValue
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject
import kotlin.native.Platform

@Composable
internal actual fun PlatformForestWebView(
    endpoint: ForestEndpoint,
    controller: ForestWebController,
    onMessage: (String) -> Unit,
    onPageStarted: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val message by rememberUpdatedState(onMessage)
    val started by rememberUpdatedState(onPageStarted)
    val error by rememberUpdatedState(onError)
    if (endpoint.isLocalHttp && !Platform.isDebugBinary) {
        LaunchedEffect(Unit) { error("숲 화면의 HTTPS 주소를 설정해 주세요.") }
        return
    }
    val delegate = remember(endpoint) { ForestWebDelegate(endpoint, { message(it) }, { started() }, { error(it) }) }
    UIKitView(
        modifier = modifier,
        factory = {
            val config = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                preferences.javaScriptCanOpenWindowsAutomatically = false
                userContentController.addScriptMessageHandler(delegate, "RingoutForestNative")
            }
            WKWebView(CGRectZero.readValue(), config).apply {
                navigationDelegate = delegate
                allowsBackForwardNavigationGestures = false
                scrollView.bounces = false
                controller.evaluate = { script ->
                    if (endpoint.allowsDocument(URL?.absoluteString)) evaluateJavaScript(script, null)
                }
                loadRequest(NSURLRequest(NSURL(string = endpoint.url)))
            }
        },
        onRelease = { view ->
            controller.evaluate = null
            view.stopLoading()
            view.navigationDelegate = null
            view.configuration.userContentController.removeScriptMessageHandlerForName("RingoutForestNative")
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

private class ForestWebDelegate(
    private val endpoint: ForestEndpoint,
    private val onMessage: (String) -> Unit,
    private val onStarted: () -> Unit,
    private val onError: (String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        if (didReceiveScriptMessage.frameInfo.mainFrame && endpoint.allowsDocument(didReceiveScriptMessage.frameInfo.request.URL?.absoluteString)) {
            (didReceiveScriptMessage.body as? String)?.let(onMessage)
        }
    }
    override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
        decisionHandler(if (decidePolicyForNavigationAction.targetFrame?.mainFrame == true && endpoint.allowsDocument(decidePolicyForNavigationAction.request.URL?.absoluteString)) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) { onStarted() }
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) { onError("숲 화면에 연결하지 못했어요. 연결 상태를 확인해 주세요.") }
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) { onError("숲 화면을 불러오지 못했어요.") }
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) { onError("숲 화면이 종료됐어요. 다시 시도해 주세요.") }
}
