package com.joon.ringout.alarm

import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AlarmIdFactoryTest {
    @Test
    fun createsCanonicalUniqueUuidStrings() {
        val first = newAlarmId()
        val second = newAlarmId()

        assertNotEquals(first, second)
        assertEquals(first, assertNotNull(NSUUID(uUIDString = first)).UUIDString)
        assertEquals(second, assertNotNull(NSUUID(uUIDString = second)).UUIDString)
    }
}
