package com.joon.ringout.presentation.termsagreement

import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.terms_privacy_document_url
import ringout.shared.generated.resources.terms_service_document_url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TermDocumentResourceTest {
    @Test
    fun mapsEveryCurrentTermToItsDocumentResource() {
        assertEquals(
            Res.string.terms_service_document_url,
            termDocumentResource(TermId.Service),
        )
        assertEquals(
            Res.string.terms_privacy_document_url,
            termDocumentResource(TermId.Privacy),
        )
        assertNull(termDocumentResource(TermId("unknown")))
    }
}
