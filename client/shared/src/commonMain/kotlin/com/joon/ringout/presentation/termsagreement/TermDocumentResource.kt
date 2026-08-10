package com.joon.ringout.presentation.termsagreement

import org.jetbrains.compose.resources.StringResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.terms_privacy_document_url
import ringout.shared.generated.resources.terms_service_document_url

fun termDocumentResource(termId: TermId): StringResource? = when (termId) {
    TermId.Service -> Res.string.terms_service_document_url
    TermId.Privacy -> Res.string.terms_privacy_document_url
    else -> null
}
