package com.joon.ringout.presentation.login

import android.app.Activity
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
        if (activityResult.resultCode != Activity.RESULT_OK) {
            currentOnResult.value(GoogleAccessTokenResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        val data = activityResult.data
        if (data == null) {
            currentOnResult.value(
                GoogleAccessTokenResult.Failure("Google 인증 응답이 비어 있어요."),
            )
            return@rememberLauncherForActivityResult
        }

        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data)
        }.onSuccess { result ->
            currentOnResult.value(result.toAccessTokenResult())
        }.onFailure { error ->
            currentOnResult.value(error.toGoogleAccessTokenFailure())
        }
    }

    return remember(authorizationClient, resolutionLauncher) {
        {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(GoogleIdentityScopes)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    val pendingIntent = result.pendingIntent
                    if (result.hasResolution() && pendingIntent != null) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    } else {
                        currentOnResult.value(result.toAccessTokenResult())
                    }
                }
                .addOnFailureListener { error ->
                    currentOnResult.value(error.toGoogleAccessTokenFailure())
                }
        }
    }
}

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
