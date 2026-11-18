@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.preferences

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstallationPreferencesDataStoreTest {
    @Test
    fun `설치 상태 파일을 교체해도 전용 디렉터리의 백업 제외 속성이 유지된다`() = withTemporaryPath { base ->
        val path = prepareInstallationPreferencesPath(NSURL.fileURLWithPath(base.toString(), isDirectory = true))

        assertEquals(InstallationPreferencesFileName, path.name)
        assertEquals("ringout.installation", path.parent?.name)
        assertBackupExcluded(path.parent!!)

        FileSystem.SYSTEM.write(path) { writeUtf8("before") }
        val replacement = path.parent!! / "${path.name}.tmp"
        FileSystem.SYSTEM.write(replacement) { writeUtf8("after") }
        FileSystem.SYSTEM.atomicMove(replacement, path)

        assertBackupExcluded(path.parent!!)
        assertEquals("after", FileSystem.SYSTEM.read(path) { readUtf8() })
    }

    @Test
    fun `설치 상태 디렉터리를 만들 수 없으면 초기화 오류를 반환한다`() = withTemporaryPath { base ->
        FileSystem.SYSTEM.write(base) { writeUtf8("file blocks the directory") }

        assertFailsWith<IOException> {
            prepareInstallationPreferencesPath(NSURL.fileURLWithPath(base.toString(), isDirectory = true))
        }
    }

    private fun assertBackupExcluded(directory: Path) {
        val values = NSURL.fileURLWithPath(directory.toString(), isDirectory = true)
            .resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error = null)
        assertTrue((values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true)
    }

    private fun withTemporaryPath(block: (Path) -> Unit) {
        val path = (NSTemporaryDirectory() + "ringout-installation-test-${NSUUID().UUIDString}").toPath()
        try {
            block(path)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(path, mustExist = false)
        }
    }
}
