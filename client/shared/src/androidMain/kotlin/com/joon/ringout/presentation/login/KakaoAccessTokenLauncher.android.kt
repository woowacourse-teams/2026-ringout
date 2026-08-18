package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
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

    return remember(context) {
        {
            val accountLoginCallback = kakaoLoginCallback(currentOnResult.value)
            if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                    when {
                        error == null -> currentOnResult.value(token.toKakaoAccessTokenResult())
                        error.isKakaoLoginCancellation() -> {
                            currentOnResult.value(KakaoAccessTokenResult.Cancelled)
                        }

                        else -> {
                            UserApiClient.instance.loginWithKakaoAccount(
                                context = context,
                                callback = accountLoginCallback,
                            )
                        }
                    }
                }
            } else {
                UserApiClient.instance.loginWithKakaoAccount(
                    context = context,
                    callback = accountLoginCallback,
                )
            }
        }
    }
}

private fun kakaoLoginCallback(
    onResult: (KakaoAccessTokenResult) -> Unit,
): (OAuthToken?, Throwable?) -> Unit = { token, error ->
    when {
        error == null -> onResult(token.toKakaoAccessTokenResult())
        error.isKakaoLoginCancellation() -> onResult(KakaoAccessTokenResult.Cancelled)
        else -> onResult(
            KakaoAccessTokenResult.Failure(
                message = error.message ?: "카카오 로그인에 실패했어요.",
            ),
        )
    }
}

private fun OAuthToken?.toKakaoAccessTokenResult(): KakaoAccessTokenResult {
    val accessToken = this?.accessToken
    return if (accessToken.isNullOrBlank()) {
        KakaoAccessTokenResult.Failure("카카오 Access Token을 받지 못했어요.")
    } else {
        KakaoAccessTokenResult.Success(accessToken)
    }
}

private fun Throwable.isKakaoLoginCancellation(): Boolean =
    this is ClientError && reason == ClientErrorCause.Cancelled
