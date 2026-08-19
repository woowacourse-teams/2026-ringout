package com.joon.ringout.presentation.login

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

@Composable
internal actual fun rememberGoogleAccessTokenLauncher(
    onResult: (GoogleAccessTokenResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val authorizationClient = remember(context) {
        Identity.getAuthorizationClient(context)
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        Log.d(GOOGLE_AUTH_LOG_TAG, "google_resolution_result code=${activityResult.resultCode}")
        if (activityResult.resultCode != Activity.RESULT_OK) {
            Log.d(GOOGLE_AUTH_LOG_TAG, "google_resolution_cancelled")
            currentOnResult.value(GoogleAccessTokenResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        val data = activityResult.data
        if (data == null) {
            Log.e(GOOGLE_AUTH_LOG_TAG, "google_resolution_failed reason=empty_intent")
            currentOnResult.value(
                GoogleAccessTokenResult.Failure("Google 인증 응답이 비어 있어요."),
            )
            return@rememberLauncherForActivityResult
        }

        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data)
        }.onSuccess { result ->
            Log.d(
                GOOGLE_AUTH_LOG_TAG,
                "google_resolution_parsed token_received=${!result.accessToken.isNullOrBlank()}",
            )
            currentOnResult.value(result.toAccessTokenResult())
        }.onFailure { error ->
            Log.e(
                GOOGLE_AUTH_LOG_TAG,
                "google_resolution_parse_failed cause=${error::class.simpleName} " +
                    "message=${error.message.orEmpty()}",
            )
            currentOnResult.value(error.toGoogleAccessTokenFailure())
        }
    }

    return remember(authorizationClient, resolutionLauncher) {
        {
            Log.d(GOOGLE_AUTH_LOG_TAG, "google_authorize_requested")
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(GoogleIdentityScopes)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    val pendingIntent = result.pendingIntent
                    Log.d(
                        GOOGLE_AUTH_LOG_TAG,
                        "google_authorize_succeeded has_resolution=${result.hasResolution()} " +
                            "token_received=${!result.accessToken.isNullOrBlank()}",
                    )
                    if (result.hasResolution() && pendingIntent != null) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    } else {
                        currentOnResult.value(result.toAccessTokenResult())
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(
                        GOOGLE_AUTH_LOG_TAG,
                        "google_authorize_failed cause=${error::class.simpleName} " +
                            "message=${error.message.orEmpty()}",
                    )
                    currentOnResult.value(error.toGoogleAccessTokenFailure())
                }
        }
    }
}

private const val GOOGLE_AUTH_LOG_TAG = "RingoutAuth"

private fun AuthorizationResult.toAccessTokenResult(): GoogleAccessTokenResult {
    val accessToken = accessToken
    return if (accessToken.isNullOrBlank()) {
        GoogleAccessTokenResult.Failure("Google Access Token을 받지 못했어요.")
    } else {
        GoogleAccessTokenResult.Success(accessToken)
    }
}

private fun Throwable.toGoogleAccessTokenFailure(): GoogleAccessTokenResult.Failure =
    GoogleAccessTokenResult.Failure(
        message = message ?: "Google 로그인에 실패했어요.",
    )

private val GoogleIdentityScopes = listOf(
    Scope("openid"),
    Scope("email"),
)
