package com.kyusui.app.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan izin `POST_NOTIFICATIONS`.
 *
 * Yang diuji adalah keputusan produksi [NotificationPermission.isRequired],
 * bukan dialog sistem. Dialog itu hanya ada di perangkat/emulator dan tidak
 * bisa diverifikasi di unit test JVM.
 */
class NotificationPermissionTest {

    @Test
    fun `permission is not required before android 13`() {
        assertFalse(NotificationPermission.isRequired(24))
        assertFalse(NotificationPermission.isRequired(32))
    }

    @Test
    fun `permission is required from android 13 onward`() {
        assertTrue(NotificationPermission.isRequired(33))
        assertTrue(NotificationPermission.isRequired(34))
        assertTrue(NotificationPermission.isRequired(35))
    }

    @Test
    fun `the runtime permission name is the platform constant`() {
        assertEquals("android.permission.POST_NOTIFICATIONS", NotificationPermission.POST_NOTIFICATIONS)
        assertEquals(33, NotificationPermission.MIN_SDK_WITH_RUNTIME_PERMISSION)
    }
}
