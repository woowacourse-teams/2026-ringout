package com.joon.ringout.domain.terms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentTermsTest {
    @Test
    fun currentTermsContainOnlyTheTwoRequiredVersionOneTerms() {
        assertEquals(listOf(TermId.Service, TermId.Privacy), currentTerms.map(TermDefinition::id))
        assertTrue(currentTerms.all { definition -> definition.version == "1" })
        assertTrue(currentTerms.all { definition -> definition.type == TermType.REQUIRED })
    }

    @Test
    fun termTypesUseStablePersistedValues() {
        assertEquals("required", TermType.REQUIRED.persistedValue)
        assertEquals("optional", TermType.OPTIONAL.persistedValue)
        assertEquals(TermType.REQUIRED, TermType.fromPersistedValue("required"))
        assertEquals(TermType.OPTIONAL, TermType.fromPersistedValue("optional"))
        assertNull(TermType.fromPersistedValue("unknown"))
    }
}
