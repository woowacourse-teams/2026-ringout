package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberGoogleIdTokenLauncher(
    serverClientId: String,
    onResult: (GoogleIdTokenResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    return remember(context, credentialManager, coroutineScope, serverClientId) {
        {
            if (serverClientId.isBlank()) {
                currentOnResult(
                    GoogleIdTokenResult.Failure(
                        "GOOGLE_SERVER_CLIENT_ID가 설정되지 않았어요.",
                    ),
                )
            } else {
                coroutineScope.launch {
                    try {
                        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleOption)
                            .build()
                        val credential = credentialManager.getCredential(
                            context = context,
                            request = request,
                        ).credential
                        val customCredential = credential as? CustomCredential
                        check(
                            customCredential?.type ==
                                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
                        ) {
                            "Google 로그인 응답 형식을 확인할 수 없어요."
                        }
                        val idToken = GoogleIdTokenCredential
                            .createFrom(customCredential.data)
                            .idToken
                        currentOnResult(GoogleIdTokenResult.Success(idToken))
                    } catch (_: GetCredentialCancellationException) {
                        currentOnResult(GoogleIdTokenResult.Cancelled)
                    } catch (error: Throwable) {
                        currentOnResult(
                            GoogleIdTokenResult.Failure(
                                error.message ?: "Google 로그인에 실패했어요.",
                            ),
                        )
                    }
                }
            }
        }
    }
}
