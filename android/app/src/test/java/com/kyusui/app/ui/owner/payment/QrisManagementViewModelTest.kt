package com.kyusui.app.ui.owner.payment

import android.net.Uri
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.ProofUploadPolicy
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.model.OwnerQrisConfig
import com.kyusui.app.domain.model.ProofImageSelection
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test manajemen QRIS aktif oleh Owner.
 *
 * Yang dijaga test ini:
 *
 * - konfigurasi QRIS aktif hanya dibaca dari `GET /owner/payment/qris` dan
 *   tidak pernah di-hardcode;
 * - penggantian hanya dikirim setelah Owner benar-benar memilih file, dan file
 *   yang sama tidak terkirim dua kali;
 * - hasil penggantian adalah konfigurasi dari backend, bukan file lokal;
 * - `404` berarti QRIS belum dikonfigurasi dan dibedakan dari error lain;
 * - pre-check ukuran dan format menolak file yang jelas tidak berguna, tanpa
 *   pernah menyentuh backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QrisManagementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OwnerRepository
    private lateinit var proofFileSource: ProofFileSource

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        proofFileSource = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Membaca QRIS aktif ---

    @Test
    fun `null qris data maps to empty state`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(null)
        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.qrisState is LoadState.Empty)
        assertTrue(viewModel.uiState.value.isQrisNotConfigured)
        assertNull(viewModel.uiState.value.config)
    }

    @Test
    fun `active qris is loaded on init`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getActiveQris() }
        assertEquals("/storage/qris/active-qris.png", viewModel.uiState.value.config?.qrisImage)
        assertTrue(viewModel.uiState.value.config?.isDisplayable == true)
    }

    @Test
    fun `active qris without image is not displayable`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(
            OwnerQrisConfig(qrisImage = null)
        )

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()

        val config = requireNotNull(viewModel.uiState.value.config)
        assertNull(config.qrisImage)
        assertFalse(config.isDisplayable)
    }

    @Test
    fun `not found active qris is reported as not configured`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns
            Result.failure<OwnerQrisConfig>(NotFoundException())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()

        // 404 di sini berarti belum ada QRIS aktif, yaitu kondisi bisnis yang
        // tidak berubah dengan tombol coba lagi.
        assertTrue(viewModel.uiState.value.isQrisNotConfigured)
    }

    @Test
    fun `unauthorized active qris is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.failure(UnauthorizedException())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()

        val state = viewModel.uiState.value.qrisState
        assertTrue(state is LoadState.Error)
        assertTrue((state as LoadState.Error).isUnauthorized)
    }

    @Test
    fun `forbidden active qris is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.failure(ForbiddenException())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.qrisState is LoadState.Error)
    }

    @Test
    fun `refresh reloads the configuration`() = runTest(dispatcher) {
        val calls = intArrayOf(0)
        coEvery { repository.getActiveQris() } answers {
            calls[0]++
            Result.Success(
                if (calls[0] == 1) {
                    config(qrisImage = "/storage/qris/old.png")
                } else {
                    config(qrisImage = "/storage/qris/new.png")
                }
            )
        }

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        assertEquals("/storage/qris/old.png", viewModel.uiState.value.config?.qrisImage)

        viewModel.loadActiveQris()
        advanceUntilIdle()

        assertEquals("/storage/qris/new.png", viewModel.uiState.value.config?.qrisImage)
        coVerify(exactly = 2) { repository.getActiveQris() }
    }

    // --- Pemilihan file ---

    @Test
    fun `picked file becomes the pending replacement`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()

        assertEquals("image/png", viewModel.uiState.value.selectedImage?.mimeType)
        assertTrue(viewModel.uiState.value.canReplace)
    }

    @Test
    fun `unreadable file is reported without touching the backend`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns null

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()

        // File tidak terbaca adalah kondisi lokal. Menolak lebih awal
        // mencegah mengirim request yang pasti ditolak backend.
        assertTrue(viewModel.uiState.value.selectionState is SelectionState.Error)
        assertNull(viewModel.uiState.value.selectedImage)
        assertFalse(viewModel.uiState.value.canReplace)
        coVerify(exactly = 0) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `file that is too large is rejected before upload`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection(
            sizeBytes = ProofUploadPolicy.MAX_SIZE_BYTES + 1
        )

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectionState is SelectionState.Error)
        coVerify(exactly = 0) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `file with a disallowed mime type is rejected before upload`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection(
            mimeType = "application/pdf"
        )

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectionState is SelectionState.Error)
        coVerify(exactly = 0) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `clearing the selection removes the pending replacement`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canReplace)

        viewModel.clearSelection()

        assertNull(viewModel.uiState.value.selectedImage)
        assertEquals(SelectionState.Empty, viewModel.uiState.value.selectionState)
        assertFalse(viewModel.uiState.value.canReplace)
    }

    // --- Mengganti QRIS ---

    @Test
    fun `replacement is not sent without a selected file`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        // Tidak ada keadaan "ganti QRIS" tanpa file.
        coVerify(exactly = 0) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `replacement is sent for the selected file`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.Success(
            config(qrisImage = "/storage/qris/new-qris.png")
        )

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `configuration after replacement comes from the backend`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(
            config(qrisImage = "/storage/qris/old-qris.png")
        )
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.Success(
            config(qrisImage = "/storage/qris/new-qris.png")
        )

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        // QRIS aktif yang ditampilkan adalah yang dikembalikan server, bukan
        // file lokal yang baru dipilih Owner.
        assertEquals(
            "/storage/qris/new-qris.png",
            viewModel.uiState.value.config?.qrisImage
        )
        assertEquals(
            QrisManagementViewModel.QRIS_REPLACED_MESSAGE,
            viewModel.uiState.value.replacementMessage
        )
        // File lokal sudah tidak relevan setelah server mengonfirmasi.
        assertNull(viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `duplicate replacement is sent only once`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.Success(config())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        // Satu file tidak boleh terkirim dua kali.
        coVerify(exactly = 1) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `failed replacement keeps the previous configuration`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(
            config(qrisImage = "/storage/qris/old-qris.png")
        )
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.failure(ValidationException(
            message = "Ukuran berkas melebihi batas."
        ))

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        // Penolakan backend tidak boleh mengganti QRIS aktif di Android.
        assertEquals(
            "/storage/qris/old-qris.png",
            viewModel.uiState.value.config?.qrisImage
        )
        assertTrue(viewModel.uiState.value.replacementErrorMessage != null)
        assertNull(viewModel.uiState.value.replacementMessage)
        assertFalse(viewModel.uiState.value.isReplacing)
    }

    @Test
    fun `forbidden replacement is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.failure(ForbiddenException())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.replacementState is SubmitState.Error)
    }

    @Test
    fun `unauthorized replacement is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.failure(UnauthorizedException())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()

        val submit = viewModel.uiState.value.replacementState
        assertTrue(submit is SubmitState.Error)
        assertTrue((submit as SubmitState.Error).isUnauthorized)
    }

    @Test
    fun `replacement can be retried after a failure`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        val calls = intArrayOf(0)
        coEvery { repository.replaceActiveQris(any()) } answers {
            calls[0]++
            if (calls[0] == 1) {
                Result.failure<OwnerQrisConfig>(
                    ValidationException(message = "Berkas ditolak.")
                )
            } else {
                Result.Success(config(qrisImage = "/storage/qris/new-qris.png"))
            }
        }

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.replacementState is SubmitState.Error)

        viewModel.replaceActiveQris()
        advanceUntilIdle()

        assertEquals(
            "/storage/qris/new-qris.png",
            viewModel.uiState.value.config?.qrisImage
        )
        coVerify(exactly = 2) { repository.replaceActiveQris(any()) }
    }

    @Test
    fun `clearing the replacement message resets the submit state`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.Success(config())

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.replacementMessage != null)

        viewModel.clearReplacementMessage()

        assertNull(viewModel.uiState.value.replacementMessage)
        assertEquals(SubmitState.Idle, viewModel.uiState.value.replacementState)
    }

    @Test
    fun `reloading the active qris resets the replacement submit state`() = runTest(dispatcher) {
        coEvery { repository.getActiveQris() } returns Result.Success(config())
        coEvery { proofFileSource.readSelection(any()) } returns selection()
        coEvery { repository.replaceActiveQris(any()) } returns Result.failure(ValidationException(
            message = "Berkas ditolak."
        ))

        val viewModel = QrisManagementViewModel(repository, proofFileSource)
        advanceUntilIdle()
        viewModel.onQrisImagePicked(contentUri())
        advanceUntilIdle()
        viewModel.replaceActiveQris()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.replacementState is SubmitState.Error)

        viewModel.loadActiveQris()
        advanceUntilIdle()

        // Aksi lama sudah tidak berlaku setelah Owner memuat ulang.
        assertEquals(SubmitState.Idle, viewModel.uiState.value.replacementState)
    }

    // --- Fixtures ---

    /**
     * `Uri` adalah kelas Android, jadi tidak bisa dibuat dengan `Uri.parse`
     * pada unit test JVM. Owner hanya meneruskan URI hasil picker ke
     * repository, jadi test cukup memakai stub.
     */
    private fun contentUri(): Uri = mockk(relaxed = true)

    private fun config(
        qrisImage: String? = "/storage/qris/active-qris.png"
    ) = OwnerQrisConfig(
        qrisImage = qrisImage,
        updatedAt = "2026-09-30T09:00:00Z",
        updatedBy = "5"
    )

    private fun selection(
        mimeType: String = "image/png",
        sizeBytes: Long = 1024L
    ) = ProofImageSelection(
        localUri = "content://media/1",
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        displayName = "active-qris.png"
    )
}