package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

@Composable
internal actual fun rememberKakaoAccessTokenLauncher(
    onResult: (KakaoAccessTokenResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val lifetime = remember(context) { KakaoSignInLifetime() }
    DisposableEffect(lifetime) {
        lifetime.active = true
        onDispose { lifetime.active = false }
    }

    return remember(context, lifetime) {
        val complete: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (lifetime.active) currentOnResult.value(kakaoAccessTokenResult(token?.accessToken, error))
        }
        val accountLogin: () -> Unit = {
            if (lifetime.active) {
                runCatching {
                    UserApiClient.instance.loginWithKakaoAccount(context, callback = complete)
                }.onFailure { complete(null, it) }
            }
        }
        val launch: () -> Unit = {
            runCatching {
                if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                    runCatching {
                        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                            if (lifetime.active) {
                                if (error != null && !error.isKakaoLoginCancellation()) {
                                    accountLogin()
                                } else {
                                    complete(token, error)
                                }
                            }
                        }
                    }.onFailure { error ->
                        if (error.isKakaoLoginCancellation()) complete(null, error)
                        else accountLogin()
                    }
                } else {
                    accountLogin()
                }
            }.onFailure { complete(null, it) }
        }
        launch
    }
}

private class KakaoSignInLifetime(var active: Boolean = true)

internal fun kakaoAccessTokenResult(accessToken: String?, error: Throwable?): KakaoAccessTokenResult =
    when {
        error?.isKakaoLoginCancellation() == true -> KakaoAccessTokenResult.Cancelled
        error != null -> KakaoAccessTokenResult.Failure("카카오 로그인에 실패했어요. 다시 시도해 주세요.")
        accessToken.isNullOrBlank() -> KakaoAccessTokenResult.Failure("카카오 Access Token을 받지 못했어요.")
        else -> KakaoAccessTokenResult.Success(accessToken)
    }

private fun Throwable.isKakaoLoginCancellation(): Boolean =
    this is ClientError && reason == ClientErrorCause.Cancelled
