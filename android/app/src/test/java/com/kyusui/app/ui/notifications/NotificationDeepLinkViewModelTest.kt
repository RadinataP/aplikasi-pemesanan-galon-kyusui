package com.kyusui.app.ui.notifications

import com.kyusui.app.core.Result
import com.kyusui.app.data.fcm.FcmMessageTransport
import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import com.kyusui.app.domain.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test penerjemahan Intent system notification menjadi pending payload, serta
 * pendaftaran device token.
 *
 * Yang diuji di sini adalah keputusan produksi, bukan salinannya: payload
 * berasal dari parser repository yang sama dengan `onMessageReceived`, dan
 * token hanya didaftarkan bila Firebase menyediakannya.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDeepLinkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var fcmTransport: FcmMessageTransport
    private lateinit var viewModel: NotificationDeepLinkViewModel

    private val payload = NotificationPayload(
        notificationId = "n-1",
        type = NotificationEvent.ORDER_CREATED,
        resourceType = NotificationResourceType.ORDER,
        orderId = "1001"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        notificationRepository = mockk(relaxed = true)
        fcmTransport = mockk(relaxed = true)
        viewModel = NotificationDeepLinkViewModel(notificationRepository, fcmTransport)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Intent parsing ---

    @Test
    fun `a valid payload is kept as pending`() {
        every { notificationRepository.parsePayload(any()) } returns Result.success(payload)

        val handled = viewModel.onNotificationIntent(mapOf("type" to "ORDER_CREATED"))

        assertTrue(handled)
        assertSame(payload, viewModel.pendingNotification.value)
    }

    @Test
    fun `an unparseable payload is ignored`() {
        every { notificationRepository.parsePayload(any()) } returns Result.failure("payload rusak")

        val handled = viewModel.onNotificationIntent(mapOf("type" to "UNKNOWN"))

        assertFalse(handled)
        assertNull(viewModel.pendingNotification.value)
    }

    @Test
    fun `an intent without extras is ignored`() {
        val handled = viewModel.onNotificationIntent(emptyMap())

        assertFalse(handled)
        assertNull(viewModel.pendingNotification.value)
    }

    @Test
    fun `consuming clears the pending payload so a single intent navigates once`() {
        every { notificationRepository.parsePayload(any()) } returns Result.success(payload)
        viewModel.onNotificationIntent(mapOf("type" to "ORDER_CREATED"))

        viewModel.consumePendingNotification()

        assertNull(viewModel.pendingNotification.value)
        // Konsumsi kedua tetap aman (idempoten).
        viewModel.consumePendingNotification()
        assertNull(viewModel.pendingNotification.value)
    }

    // --- Device token registration ---

    @Test
    fun `syncDeviceToken registers the token with the backend`() = runTest(dispatcher) {
        coEvery { fcmTransport.getToken() } returns "fcm-token"

        viewModel.syncDeviceToken()
        advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.registerDeviceToken("fcm-token") }
    }

    @Test
    fun `syncDeviceToken is a no-op when firebase is not configured`() = runTest(dispatcher) {
        coEvery { fcmTransport.getToken() } returns null

        viewModel.syncDeviceToken()
        advanceUntilIdle()

        coVerify(exactly = 0) { notificationRepository.registerDeviceToken(any()) }
    }
}
