package com.joon.ringout.presentation.profilechange

import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.ProfileImageUpload
import com.joon.ringout.domain.member.MemberProfileImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileChangeViewModelTest {
    @Test
    fun `닉네임만 변경하면 사진을 업로드하지 않는다`() = runTest {
        val repository = FakeMemberRepository()
        val viewModel = ProfileChangeViewModel("기존닉네임", repository, this)
        viewModel.onNicknameChange("새닉네임")
        viewModel.confirm()
        runCurrent()
        assertEquals(listOf("새닉네임"), repository.requests)
        assertEquals(0, repository.uploads.size)
        assertEquals("새닉네임", viewModel.uiState.completedNickname)
        assertFalse(viewModel.uiState.isSaving)
        viewModel.consumeCompletedNickname()
        assertNull(viewModel.uiState.completedNickname)
    }

    @Test
    fun `사진만 변경하면 닉네임 요청 없이 원본 사진을 저장한다`() = runTest {
        val repository = FakeMemberRepository()
        val viewModel = ProfileChangeViewModel("기존닉네임", repository, this)
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        assertEquals(listOf(Photo), repository.uploads)
        assertEquals(emptyList(), repository.requests)
        assertEquals(SavedUrl, viewModel.uiState.profileImageUrl)
        assertFalse(viewModel.uiState.hasImageChanges)
        assertEquals("기존닉네임", viewModel.uiState.completedNickname)
    }

    @Test
    fun `닉네임 저장 후 사진이 실패하면 사진만 재시도한다`() = runTest {
        val repository = FakeMemberRepository().apply { failUpload = true }
        val viewModel = ProfileChangeViewModel("기존닉네임", repository, this)
        viewModel.onNicknameChange("새닉네임")
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        assertNull(viewModel.uiState.completedNickname)
        assertTrue(viewModel.uiState.hasImageChanges)
        assertTrue(viewModel.uiState.errorMessage.orEmpty().contains("닉네임은 저장됐어요"))
        repository.failUpload = false
        viewModel.confirm()
        runCurrent()
        assertEquals(listOf("새닉네임"), repository.requests)
        assertEquals(listOf(Photo, Photo), repository.uploads)
        assertEquals("새닉네임", viewModel.uiState.completedNickname)
    }

    @Test
    fun `저장 중 중복 저장과 사진 변경을 차단한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeMemberRepository().apply { beforeUpload = { gate.await() } }
        val viewModel = ProfileChangeViewModel("닉네임", repository, this)
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        assertTrue(viewModel.uiState.isSaving)
        viewModel.confirm()
        assertFalse(viewModel.onProfileImageSelected(Photo))
        viewModel.onNicknameChange("변경금지")
        assertEquals("닉네임", viewModel.uiState.nickname)
        gate.complete(Unit)
        runCurrent()
        viewModel.confirm()
        runCurrent()
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun `닉네임 저장 실패 시 사진 저장이나 완료 처리를 하지 않는다`() = runTest {
        val repository = FakeMemberRepository().apply { failNickname = true }
        val viewModel = ProfileChangeViewModel("닉네임", repository, this)
        viewModel.onNicknameChange("새닉네임")
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        assertEquals(0, repository.uploads.size)
        assertNull(viewModel.uiState.completedNickname)
        assertTrue(viewModel.uiState.hasImageChanges)
        assertFalse(viewModel.uiState.isSaving)
    }

    @Test
    fun `사진 저장 뒤 도착한 기존 사진 조회 결과가 새 사진을 덮어쓰지 않는다`() = runTest {
        val oldImage = CompletableDeferred<MemberProfileImage>()
        val repository = FakeMemberRepository().apply {
            cachedImage = null
            imageLoader = { oldImage.await() }
        }
        val viewModel = ProfileChangeViewModel("닉네임", repository, this)
        runCurrent()
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        oldImage.complete(MemberProfileImage("https://example.com/old.jpg"))
        runCurrent()
        assertEquals(SavedUrl, viewModel.uiState.profileImageUrl)
    }

    @Test
    fun `현재 사진 조회가 실패해도 새 사진을 저장할 수 있다`() = runTest {
        val repository = FakeMemberRepository().apply {
            cachedImage = null
            imageLoader = { error("조회 실패") }
        }
        val viewModel = ProfileChangeViewModel("닉네임", repository, this)
        runCurrent()
        viewModel.onProfileImageSelected(Photo)
        viewModel.confirm()
        runCurrent()
        assertEquals(SavedUrl, viewModel.uiState.profileImageUrl)
        assertEquals("닉네임", viewModel.uiState.completedNickname)
    }
}

private val Photo = ProfileImageUpload(byteArrayOf(1, 2, 3), "image/jpeg", "profile.jpg")
private const val SavedUrl = "https://example.com/new.jpg"

private class FakeMemberRepository : MemberRepository {
    val requests = mutableListOf<String>()
    val uploads = mutableListOf<ProfileImageUpload>()
    var cachedImage: MemberProfileImage? = MemberProfileImage(null)
    var imageLoader: suspend () -> MemberProfileImage = { MemberProfileImage(null) }
    var beforeUpload: suspend () -> Unit = {}
    var failUpload = false
    var failNickname = false

    override fun getCachedProfileImage(): MemberProfileImage? = cachedImage
    override suspend fun uploadProfileImage(image: ProfileImageUpload): MemberProfileImage {
        uploads += image
        beforeUpload()
        if (failUpload) error("업로드 실패")
        return MemberProfileImage(SavedUrl).also { cachedImage = it }
    }
    override suspend fun getProfileImage(): MemberProfileImage = imageLoader()
    override suspend fun getProfile(): MemberProfile = error("사용하지 않는 요청입니다.")
    override suspend fun updateNickname(nickname: String): String {
        requests += nickname
        if (failNickname) error("닉네임 변경 실패")
        return nickname
    }
    override suspend fun withdraw() = Unit
}
