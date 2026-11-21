package com.kyusui.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Klasifikasi status order yang dipakai tracking customer.
 *
 * `isTrackable()` menentukan tombol lacak, `isTerminal()` menentukan kapan
 * polling tracking harus berhenti.
 */
class OrderStatusTest {

    @Test
    fun `only assigned and in-delivery orders are trackable`() {
        assertTrue(OrderStatus.DITUGASKAN.isTrackable())
        assertTrue(OrderStatus.DALAM_PENGANTARAN.isTrackable())
        assertFalse(OrderStatus.MENUNGGU_PEMBAYARAN.isTrackable())
        assertFalse(OrderStatus.MENUNGGU_DIPROSES.isTrackable())
        assertFalse(OrderStatus.DIPROSES.isTrackable())
        assertFalse(OrderStatus.SELESAI.isTrackable())
        assertFalse(OrderStatus.DIBATALKAN.isTrackable())
    }

    @Test
    fun `completed and cancelled orders are terminal`() {
        assertTrue(OrderStatus.SELESAI.isTerminal())
        assertTrue(OrderStatus.DIBATALKAN.isTerminal())
    }

    @Test
    fun `orders still in progress are not terminal`() {
        assertFalse(OrderStatus.MENUNGGU_PEMBAYARAN.isTerminal())
        assertFalse(OrderStatus.MENUNGGU_DIPROSES.isTerminal())
        assertFalse(OrderStatus.DIPROSES.isTerminal())
        assertFalse(OrderStatus.DITUGASKAN.isTerminal())
        assertFalse(OrderStatus.DALAM_PENGANTARAN.isTerminal())
    }

    @Test
    fun `terminal orders are never trackable`() {
        OrderStatus.entries
            .filter { it.isTerminal() }
            .forEach { status ->
                assertFalse("$status must not be trackable", status.isTrackable())
            }
    }
}
