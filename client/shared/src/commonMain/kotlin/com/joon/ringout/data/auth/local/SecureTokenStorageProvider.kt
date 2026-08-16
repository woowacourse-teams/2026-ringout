package com.joon.ringout.data.auth.local

import androidx.compose.runtime.Composable
import com.joon.ringout.domain.auth.SecureTokenStorage

@Composable
internal expect fun rememberSecureTokenStorage(): SecureTokenStorage
