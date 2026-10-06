package com.joon.ringout.presentation.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope

@Composable
internal actual fun rememberGoogleAccessTokenLauncher(
    onResult: (GoogleAccessTokenResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val lifetime = remember(authorizationClient) { GoogleSignInLifetime() }
    DisposableEffect(lifetime) {
        lifetime.active = true
        onDispose { lifetime.active = false }
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (lifetime.active) {
            currentOnResult.value(
                when {
                    result.resultCode == Activity.RESULT_CANCELED -> GoogleAccessTokenResult.Cancelled
                    result.resultCode != Activity.RESULT_OK || result.data == null ->
                        GoogleAccessTokenResult.Failure("Google 인증 응답을 받지 못했어요.")
                    else -> runCatching {
                        authorizationClient.getAuthorizationResultFromIntent(result.data)
                            .toAccessTokenResult()
                    }.getOrElse { it.toGoogleAccessTokenResult() }
                },
            )
        }
    }

    return remember(authorizationClient, resolutionLauncher, lifetime) {
        {
            runCatching {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(GoogleIdentityScopes)
                    .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                    .build()
                authorizationClient.authorize(request)
                    .addOnSuccessListener { result ->
                        if (lifetime.active) {
                            runCatching {
                                if (result.hasResolution()) {
                                    val pendingIntent = checkNotNull(result.pendingIntent)
                                    resolutionLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                    )
                                } else {
                                    currentOnResult.value(result.toAccessTokenResult())
                                }
                            }.onFailure { currentOnResult.value(it.toGoogleAccessTokenResult()) }
                        }
                    }
                    .addOnFailureListener { error ->
                        if (lifetime.active) currentOnResult.value(error.toGoogleAccessTokenResult())
                    }
            }.onFailure { error ->
                if (lifetime.active) currentOnResult.value(error.toGoogleAccessTokenResult())
            }
        }
    }
}

private class GoogleSignInLifetime(var active: Boolean = true)

private fun AuthorizationResult.toAccessTokenResult(): GoogleAccessTokenResult {
    val token = accessToken
    return if (token.isNullOrBlank()) {
        GoogleAccessTokenResult.Failure("Google Access Token을 받지 못했어요.")
    } else {
        GoogleAccessTokenResult.Success(token)
    }
}

private fun Throwable.toGoogleAccessTokenResult(): GoogleAccessTokenResult =
    if (this is ApiException && statusCode == CommonStatusCodes.CANCELED) {
        GoogleAccessTokenResult.Cancelled
    } else {
        GoogleAccessTokenResult.Failure("Google 로그인에 실패했어요. 다시 시도해 주세요.")
    }

private val GoogleIdentityScopes = listOf(Scope("openid"), Scope("email"))
