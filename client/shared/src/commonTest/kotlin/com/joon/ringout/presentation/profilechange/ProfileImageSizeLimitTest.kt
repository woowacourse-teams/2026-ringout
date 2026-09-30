package com.joon.ringout.presentation.profilechange

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileImageSizeLimitTest {
    @Test
    fun `제한보다 1바이트 작은 사진을 허용한다`() {
        assertFalse(isProfileImageSizeTooLarge(MaxProfileImageBytes - 1))
    }

    @Test
    fun `제한과 크기가 같은 사진을 허용한다`() {
        assertFalse(isProfileImageSizeTooLarge(MaxProfileImageBytes))
    }

    @Test
    fun `제한보다 1바이트 큰 사진을 거부한다`() {
        assertTrue(isProfileImageSizeTooLarge(MaxProfileImageBytes + 1))
    }
}
