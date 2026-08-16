package com.joon.ringout.presentation.termsagreement

import java.time.LocalDate

internal actual fun currentAgreementDate(): String = LocalDate.now().toString()
