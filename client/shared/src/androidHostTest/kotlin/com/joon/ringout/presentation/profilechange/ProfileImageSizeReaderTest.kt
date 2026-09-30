package com.joon.ringout.presentation.profilechange

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileImageSizeReaderTest {
    @Test
    fun `제한 크기에서 끝나는 스트림을 허용한다`() {
        val inputStream = ByteArrayInputStream(ByteArray(MaxProfileImageBytes.toInt()))

        assertFalse(isProfileImageTooLarge(inputStream))
    }

    @Test
    fun `제한을 넘는 스트림은 제한보다 1바이트만 읽고 거부한다`() {
        val inputStream = CountingInputStream()

        assertTrue(isProfileImageTooLarge(inputStream))
        assertEquals(MaxProfileImageBytes + 1, inputStream.bytesRead)
    }

    @Test
    fun `스트림 읽기 실패를 호출자에게 전달한다`() {
        val inputStream = object : InputStream() {
            override fun read(): Int = throw IOException("read failed")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw IOException("read failed")
        }

        assertFailsWith<IOException> { isProfileImageTooLarge(inputStream) }
    }

    private class CountingInputStream : InputStream() {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            bytesRead += 1
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            bytesRead += length
            return length
        }
    }
}
