package com.joon.ringout.presentation.login

import android.util.Log
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
            val isKakaoTalkAvailable = UserApiClient.instance.isKakaoTalkLoginAvailable(context)
            Log.d(KAKAO_AUTH_LOG_TAG, "kakao_login_requested talk_available=$isKakaoTalkAvailable")
            if (isKakaoTalkAvailable) {
                UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                    when {
                        error == null -> {
                            Log.d(KAKAO_AUTH_LOG_TAG, "kakao_talk_succeeded")
                            currentOnResult.value(token.toKakaoAccessTokenResult())
                        }
                        error.isKakaoLoginCancellation() -> {
                            Log.d(KAKAO_AUTH_LOG_TAG, "kakao_talk_cancelled")
                            currentOnResult.value(KakaoAccessTokenResult.Cancelled)
                        }

                        else -> {
                            Log.e(
                                KAKAO_AUTH_LOG_TAG,
                                "kakao_talk_failed_falling_back cause=${error::class.simpleName} " +
                                    "message=${error.message.orEmpty()}",
                            )
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
        error == null -> {
            Log.d(KAKAO_AUTH_LOG_TAG, "kakao_account_succeeded")
            onResult(token.toKakaoAccessTokenResult())
        }
        error.isKakaoLoginCancellation() -> {
            Log.d(KAKAO_AUTH_LOG_TAG, "kakao_account_cancelled")
            onResult(KakaoAccessTokenResult.Cancelled)
        }
        else -> {
            Log.e(
                KAKAO_AUTH_LOG_TAG,
                "kakao_account_failed cause=${error::class.simpleName} " +
                    "message=${error.message.orEmpty()}",
            )
            onResult(
                KakaoAccessTokenResult.Failure(
                    message = error.message ?: "카카오 로그인에 실패했어요.",
                ),
            )
        }
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

private const val KAKAO_AUTH_LOG_TAG = "RingoutAuth"
