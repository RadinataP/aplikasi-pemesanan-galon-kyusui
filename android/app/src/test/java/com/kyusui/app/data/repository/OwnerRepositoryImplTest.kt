package com.kyusui.app.data.repository

import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.data.api.AssignCourierRequestDto
import com.kyusui.app.data.api.CourierUserDto
import com.kyusui.app.data.api.CreateOwnerProductRequestDto
import com.kyusui.app.data.api.CustomerPaymentDto
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.data.api.OrderAssignmentDto
import com.kyusui.app.data.api.OwnerActiveQrisDto
import com.kyusui.app.data.api.OwnerApi
import com.kyusui.app.data.api.OwnerCourierDto
import com.kyusui.app.data.api.OwnerOrderDto
import com.kyusui.app.data.api.OwnerPaymentProofDto
import com.kyusui.app.data.api.OwnerPendingPaymentDto
import com.kyusui.app.data.api.OwnerProductDto
import com.kyusui.app.data.api.OwnerProofDetailDto
import com.kyusui.app.data.api.PageDto
import com.kyusui.app.data.api.PageMetaDto
import com.kyusui.app.data.api.PaymentProofDto
import com.kyusui.app.data.api.PaymentVerificationEnvelopeDto
import com.kyusui.app.data.api.PaymentVerificationRequestDto
import com.kyusui.app.data.api.UpdateOwnerOrderStatusRequestDto
import com.kyusui.app.data.api.UpdateOwnerProductRequestDto
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.model.ProofImageSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Test repository Owner.
 *
 * Fokus test ini adalah apa yang repository KIRIM dan apa yang repository
 * KEMBALIKAN:
 *
 * - repository hanya meneruskan `action` ke endpoint verifikasi, dan status pada
 *   hasil dibaca dari response backend;
 * - error HTTP non-2xx pada endpoint `Response<T>` dilempar sebagai exception
 *   agar `409` dan `422` tidak pernah terlihat sebagai aksi yang berhasil;
 * - `note` kosong tidak dikirim;
 * - nominal dan status selalu menyalin nilai backend.
 */
class OwnerRepositoryImplTest {

    private lateinit var api: OwnerApi
    private lateinit var proofFileSource: ProofFileSource
    private lateinit var repository: OwnerRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        proofFileSource = mockk(relaxed = true)
        repository = OwnerRepositoryImpl(api, proofFileSource)
    }

    // --- 10.1 daftar order ---

    @Test
    fun `order list is requested with pagination`() = runTest {
        coEvery { api.orders(any(), any(), any()) } returns orderPage()

        val result = repository.getOrders(page = 2, perPage = 10)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { api.orders(page = 2, perPage = 10, status = null) }
    }

    @Test
    fun `status filter is sent as the canonical api value`() = runTest {
        coEvery { api.orders(any(), any(), any()) } returns orderPage()

        repository.getOrders(page = 1, perPage = 20, status = OrderStatus.DIPROSES)

        // Nilai dikirim apa adanya dari enum canonical. Android tidak membuat
        // nilai status lain.
        coVerify(exactly = 1) { api.orders(page = 1, perPage = 20, status = "DIPROSES") }
    }

    @Test
    fun `order list amounts are copied from the backend`() = runTest {
        coEvery { api.orders(any(), any(), any()) } returns orderPage()

        val orders = (repository.getOrders(1, 20) as Result.Success).data.data

        assertEquals("21000.00", orders.single().totalAmount)
        assertEquals("5000.00", orders.single().deliveryFee)
    }

    // --- 10.2 detail order ---

    @Test
    fun `order detail is requested by order id`() = runTest {
        coEvery { api.order("1001") } returns EnvelopeDto(data = orderDto())

        val result = repository.getOrder("1001")

        assertTrue(result is Result.Success)
        assertEquals("1001", (result as Result.Success).data.id)
        coVerify(exactly = 1) { api.order("1001") }
    }

    @Test
    fun `order detail without data payload is reported as a failure`() = runTest {
        coEvery { api.order("1001") } returns EnvelopeDto(data = null)

        assertTrue(repository.getOrder("1001") is Result.Failure)
    }

    @Test
    fun `unauthorized order detail surfaces as a failure`() = runTest {
        coEvery { api.order("1001") } throws UnauthorizedException()

        val result = repository.getOrder("1001")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is UnauthorizedException)
    }

    // --- 10.3 update status ---

    @Test
    fun `status update sends the requested next status`() = runTest {
        coEvery { api.updateOrderStatus(any(), any()) } returns Response.success(
            EnvelopeDto(data = orderDto(orderStatus = "DIPROSES"))
        )

        repository.updateOrderStatus("1001", OrderStatus.DIPROSES, note = null)

        val slot = slot<UpdateOwnerOrderStatusRequestDto>()
        coVerify(exactly = 1) { api.updateOrderStatus("1001", capture(slot)) }

        assertEquals("DIPROSES", slot.captured.status)
        assertNull(slot.captured.note)
    }

    @Test
    fun `blank note is not sent to the backend`() = runTest {
        coEvery { api.updateOrderStatus(any(), any()) } returns Response.success(
            EnvelopeDto(data = orderDto())
        )

        repository.updateOrderStatus("1001", OrderStatus.DIPROSES, note = "   ")

        val slot = slot<UpdateOwnerOrderStatusRequestDto>()
        coVerify(exactly = 1) { api.updateOrderStatus("1001", capture(slot)) }

        assertNull(slot.captured.note)
    }

    @Test
    fun `returned status after update is read from the response`() = runTest {
        coEvery { api.updateOrderStatus(any(), any()) } returns Response.success(
            EnvelopeDto(data = orderDto(orderStatus = "DIPROSES"))
        )

        val order = (
                repository.updateOrderStatus("1001", OrderStatus.DIPROSES, null) as Result.Success
                ).data

        // Status berasal dari response, bukan dari argumen yang dikirim.
        assertEquals(OrderStatus.DIPROSES, order.orderStatus)
    }

    @Test
    fun `conflict on invalid transition is reported as a failure`() = runTest {
        coEvery { api.updateOrderStatus(any(), any()) } returns Response.error(
            CONFLICT_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.updateOrderStatus("1001", OrderStatus.SELESAI, null)

        // 409 tidak boleh terlihat sebagai sukses. Bila ini bocor jadi sukses,
        // Android akan menampilkan order yang backend tolak.
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ValidationException)
    }

    @Test
    fun `validation error on status update is reported as a failure`() = runTest {
        coEvery { api.updateOrderStatus(any(), any()) } returns Response.error(
            UNPROCESSABLE_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.updateOrderStatus("1001", OrderStatus.SELESAI, null)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ValidationException)
    }

    // --- 10.4 courier assignment ---

    @Test
    fun `assignment sends only the courier id`() = runTest {
        coEvery { api.assignCourier(any(), any()) } returns Response.success(
            EnvelopeDto(data = orderDtoWithAssignment())
        )

        repository.assignCourier("1001", courierId = "20")

        val slot = slot<AssignCourierRequestDto>()
        coVerify(exactly = 1) { api.assignCourier("1001", capture(slot)) }

        assertEquals(20L, slot.captured.courier_id)
    }

    @Test
    fun `assignment result is read from the server response`() = runTest {
        coEvery { api.assignCourier(any(), any()) } returns Response.success(
            EnvelopeDto(data = orderDtoWithAssignment())
        )

        val order = (repository.assignCourier("1001", "20") as Result.Success).data

        val assignment = requireNotNull(order.assignment)
        assertEquals("20", assignment.courierId)
        assertEquals(AssignmentStatus.ASSIGNED, assignment.status)
    }

    @Test
    fun `non numeric courier id never reaches the backend`() = runTest {
        val result = repository.assignCourier("1001", courierId = "kurir-agus")

        // Android tidak memvalidasi kurir yang ada di backend, tapi tidak boleh
        // mengirim nilai yang tidak bisa jadi `courier_id` numerik. Permintaan
        // ditolak lebih awal dan Owner tetap melihat failure.
        assertTrue(result is Result.Failure)
        coVerify(exactly = 0) { api.assignCourier(any(), any()) }
    }

    @Test
    fun `forbidden assignment is reported as a failure`() = runTest {
        coEvery { api.assignCourier(any(), any()) } returns Response.error(
            FORBIDDEN_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.assignCourier("1001", "20")

        // Penolakan HTTP harus dilempar sebagai exception, bukan diteruskan
        // sebagai response sukses yang nanti dibaca "sudah ditugaskan".
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    // --- 10.4 daftar kurir tersedia ---

    @Test
    fun `courier list is read from the backend response`() = runTest {
        coEvery { api.couriers() } returns EnvelopeDto(
            data = listOf(
                OwnerCourierDto(
                    id = 20L,
                    phone = "0812000000",
                    vehicle = "Motor",
                    user = CourierUserDto(id = 5L, name = "Budi Kurir")
                )
            )
        )

        val couriers = (repository.getCouriers() as Result.Success).data

        assertEquals(1, couriers.size)
        assertEquals("20", couriers.single().id)
        assertEquals("Budi Kurir", couriers.single().displayLabel)
        assertEquals("Motor", couriers.single().vehicle)
        coVerify(exactly = 1) { api.couriers() }
    }

    @Test
    fun `courier without a name falls back to the id label`() = runTest {
        coEvery { api.couriers() } returns EnvelopeDto(
            data = listOf(OwnerCourierDto(id = 20L))
        )

        val couriers = (repository.getCouriers() as Result.Success).data

        // Android tidak mengarang nama kurir; label memakai id apa adanya.
        assertEquals("Kurir #20", couriers.single().displayLabel)
    }

    @Test
    fun `missing courier list payload is treated as an empty list`() = runTest {
        coEvery { api.couriers() } returns EnvelopeDto(data = null)

        val couriers = (repository.getCouriers() as Result.Success).data

        assertTrue(couriers.isEmpty())
    }

    @Test
    fun `forbidden courier list is reported as a failure`() = runTest {
        coEvery { api.couriers() } throws ForbiddenException()

        val result = repository.getCouriers()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    // --- 12.1 pending queue ---

    @Test
    fun `pending queue is requested with pagination`() = runTest {
        coEvery { api.pendingPayments(any(), any()) } returns pendingPage()

        val result = repository.getPendingPayments(page = 1, perPage = 20)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { api.pendingPayments(page = 1, perPage = 20) }
    }

    @Test
    fun `pending queue keeps the status sent by the backend`() = runTest {
        coEvery { api.pendingPayments(any(), any()) } returns pendingPage()

        val payment = (repository.getPendingPayments(1, 20) as Result.Success).data.data.single()

        assertEquals(PaymentMethod.QRIS, payment.paymentMethod)
        assertEquals(PaymentStatus.WAITING_VERIFICATION, payment.paymentStatus)
        assertTrue(payment.hasProof)
    }

    @Test
    fun `empty pending queue is a success with no rows`() = runTest {
        coEvery { api.pendingPayments(any(), any()) } returns pendingPage(items = emptyList())

        val result = repository.getPendingPayments(1, 20)

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.data.isEmpty())
    }

    // --- 12.2 payment proof ---

    @Test
    fun `payment proof is requested by order id`() = runTest {
        coEvery { api.paymentProof("1001") } returns EnvelopeDto(
            data = OwnerPaymentProofDto(
                order_id = 1001L,
                payment_id = 7001L,
                payment_method = "QRIS",
                payment_status = "WAITING_VERIFICATION",
                proof = OwnerProofDetailDto(
                    available = true,
                    url = "http://10.0.2.2:8000/api/v1/owner/orders/1001/payment/proof/file"
                )
            )
        )

        val proof = (repository.getPaymentProof("1001") as Result.Success).data

        assertTrue(proof.hasProof)
        assertTrue(proof.canDisplayImage)
    }

    @Test
    fun `proof without url is not turned into a displayable image`() = runTest {
        coEvery { api.paymentProof("1001") } returns EnvelopeDto(
            data = OwnerPaymentProofDto(
                order_id = 1001L,
                payment_method = "QRIS",
                payment_status = "WAITING_VERIFICATION",
                proof = OwnerProofDetailDto(available = true)
            )
        )

        val proof = (repository.getPaymentProof("1001") as Result.Success).data

        // Android tidak menyusun URL bukti sendiri.
        assertTrue(proof.hasProof)
        assertNull(proof.proofUrl)
        assertFalse(proof.canDisplayImage)
    }

    // --- 12.3 verify ---

    @Test
    fun `approve sends only the approve action`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = PaymentVerificationEnvelopeDto(
                    payment = paymentDto(paymentStatus = "PAID")
                )
            )
        )

        repository.verifyPayment("1001", OwnerVerificationAction.APPROVE, note = null)

        val slot = slot<PaymentVerificationRequestDto>()
        coVerify(exactly = 1) { api.verifyPayment("1001", capture(slot)) }

        assertEquals("APPROVE", slot.captured.action)
        assertNull(slot.captured.note)
    }

    @Test
    fun `reject sends the note provided by the owner`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = PaymentVerificationEnvelopeDto(
                    payment = paymentDto(paymentStatus = "PENDING")
                )
            )
        )

        repository.verifyPayment(
            "1001",
            OwnerVerificationAction.REJECT,
            note = "  Bukti tidak terbaca.  "
        )

        val slot = slot<PaymentVerificationRequestDto>()
        coVerify(exactly = 1) { api.verifyPayment("1001", capture(slot)) }

        assertEquals("REJECT", slot.captured.action)
        assertEquals("Bukti tidak terbaca.", slot.captured.note)
    }

    @Test
    fun `approve result reports the status returned by the backend`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = PaymentVerificationEnvelopeDto(
                    payment = paymentDto(paymentStatus = "PAID", verifiedBy = 5L)
                )
            )
        )

        val result = (
                repository.verifyPayment("1001", OwnerVerificationAction.APPROVE, null)
                        as Result.Success
                ).data

        // PAID dibaca dari response. Bila backend mengembalikan nilai lain, nilai
        // itu yang dipakai, bukan nilai yang diharapkan Android.
        assertEquals(PaymentStatus.PAID, result.payment.paymentStatus)
        assertEquals("5", result.payment.verifiedBy)
        assertEquals(OwnerVerificationAction.APPROVE, result.action)
    }

    @Test
    fun `reject result reports the status returned by the backend`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = PaymentVerificationEnvelopeDto(
                    payment = paymentDto(paymentStatus = "PENDING")
                )
            )
        )

        val result = (
                repository.verifyPayment("1001", OwnerVerificationAction.REJECT, null)
                        as Result.Success
                ).data

        assertEquals(PaymentStatus.PENDING, result.payment.paymentStatus)
    }

    @Test
    fun `verification response without payment is reported as a failure`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } returns Response.success(
            EnvelopeDto(data = PaymentVerificationEnvelopeDto(payment = null))
        )

        val result = repository.verifyPayment("1001", OwnerVerificationAction.APPROVE, null)

        // Android tidak mengarang payment ketika backend tidak mengirimnya.
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `already verified payment rejected by the server surfaces as a failure`() = runTest {
        coEvery { api.verifyPayment(any(), any()) } throws ValidationException(
            message = "Payment has already been verified."
        )

        val result = repository.verifyPayment("1001", OwnerVerificationAction.APPROVE, null)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ValidationException)
    }

    // --- 13.1 / 13.2 QRIS aktif ---

    @Test
    fun `active qris is read from the backend response`() = runTest {
        coEvery { api.activeQris() } returns EnvelopeDto(
            data = OwnerActiveQrisDto(
                qris_image = "/storage/qris/active-qris.png",
                updated_at = "2026-09-30T09:00:00Z",
                updated_by = 5L
            )
        )

        val config = requireNotNull((repository.getActiveQris() as Result.Success).data)

        assertEquals("/storage/qris/active-qris.png", config.qrisImage)
        assertTrue(config.isDisplayable)
    }

    @Test
    fun `unconfigured qris is represented as successful empty data`() = runTest {
        coEvery { api.activeQris() } returns EnvelopeDto(data = null, message = "Data QRIS berhasil dimuat.")

        val result = repository.getActiveQris()

        assertTrue(result is Result.Success)
        assertEquals(null, (result as Result.Success).data)
    }

    @Test
    fun `qris replacement uploads the multipart part built by the file source`() = runTest {
        val part = MultipartBody.Part.createFormData(
            "qris_image",
            "active-qris.png",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType())
        )
        coEvery { proofFileSource.createImagePart(any(), any(), any()) } returns part
        coEvery { api.replaceActiveQris(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = OwnerActiveQrisDto(
                    qris_image = "/storage/qris/new-qris.png",
                    updated_at = "2026-09-30T10:00:00Z",
                    updated_by = 5L
                )
            )
        )

        val selection = ProofImageSelection(
            localUri = "content://media/1",
            mimeType = "image/png",
            sizeBytes = 3L,
            displayName = "active-qris.png"
        )

        val config = (repository.replaceActiveQris(selection) as Result.Success).data

        // Konfigurasi aktif berasal dari response backend, bukan dari file lokal.
        assertEquals("/storage/qris/new-qris.png", config.qrisImage)
        coVerify(exactly = 1) {
            proofFileSource.createImagePart(
                fieldName = "qris_image",
                fileName = "active-qris.png",
                selection = selection
            )
        }
    }

    @Test
    fun `qris replacement failure keeps the previous configuration untouched`() = runTest {
        coEvery { proofFileSource.createImagePart(any(), any(), any()) } returns
                MultipartBody.Part.createFormData(
                    "qris_image",
                    "a.png",
                    byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType())
                )
        coEvery { api.replaceActiveQris(any(), any()) } returns Response.error(
            UNPROCESSABLE_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.replaceActiveQris(
            ProofImageSelection(
                localUri = "content://media/1",
                mimeType = "image/png",
                sizeBytes = 3L,
                displayName = "a.png"
            )
        )

        // Penolakan backend tidak boleh dianggap sebagai penggantian berhasil.
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `forbidden qris management is reported as a failure`() = runTest {
        coEvery { api.activeQris() } throws ForbiddenException()

        val result = repository.getActiveQris()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    // --- Preserved: owner product management ---

    @Test
    fun `product catalog is requested with pagination`() = runTest {
        coEvery { api.products(any(), any()) } returns productPage()

        val result = repository.getProducts(page = 2, perPage = 10)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { api.products(page = 2, perPage = 10) }
    }

    @Test
    fun `product catalog copies availability and price from the backend`() = runTest {
        coEvery { api.products(any(), any()) } returns productPage(
            items = listOf(
                productDto(
                    name = "Air Galon",
                    price = "18000.00",
                    isAvailable = false
                )
            )
        )

        val products = (repository.getProducts(1, 20) as Result.Success).data.data

        assertEquals("18000.00", products.single().price)
        assertFalse(products.single().isAvailable)
    }

    @Test
    fun `create product sends trimmed fields`() = runTest {
        coEvery { api.createProduct(any()) } returns Response.success(
            EnvelopeDto(data = productDto())
        )

        repository.createProduct(
            name = "  Air Botol  ",
            description = "  600ml  ",
            price = " 5000.00 ",
            availability = true
        )

        val slot = slot<CreateOwnerProductRequestDto>()
        coVerify(exactly = 1) { api.createProduct(capture(slot)) }

        assertEquals("Air Botol", slot.captured.name)
        assertEquals("600ml", slot.captured.description)
        assertEquals("5000.00", slot.captured.price)
        assertTrue(slot.captured.availability)
    }

    @Test
    fun `created product is read from the response`() = runTest {
        coEvery { api.createProduct(any()) } returns Response.success(
            EnvelopeDto(data = productDto(id = 99L, name = "Air Botol", price = "5000.00"))
        )

        val product = (repository.createProduct("Air Botol", null, "5000.00", true)
                as Result.Success).data

        assertEquals("99", product.id)
        assertEquals("Air Botol", product.name)
        assertEquals("5000.00", product.price)
    }

    @Test
    fun `create product rejects a non successful response`() = runTest {
        coEvery { api.createProduct(any()) } returns Response.error(
            UNPROCESSABLE_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.createProduct("Air", null, "1000", true)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `update product sends only the fields provided`() = runTest {
        coEvery { api.updateProduct(any(), any()) } returns Response.success(
            EnvelopeDto(data = productDto(isAvailable = false))
        )

        repository.updateProduct(
            productId = "1001",
            name = null,
            description = null,
            price = null,
            availability = false
        )

        val slot = slot<UpdateOwnerProductRequestDto>()
        coVerify(exactly = 1) { api.updateProduct("1001", capture(slot)) }

        assertNull(slot.captured.name)
        assertNull(slot.captured.price)
        assertEquals(false, slot.captured.availability)
    }

    @Test
    fun `empty description is sent so the owner can clear it`() = runTest {
        coEvery { api.updateProduct(any(), any()) } returns Response.success(
            EnvelopeDto(data = productDto())
        )

        repository.updateProduct(
            productId = "1001",
            name = "Air Galon",
            description = "",
            price = "18000.00",
            availability = true
        )

        val slot = slot<UpdateOwnerProductRequestDto>()
        coVerify(exactly = 1) { api.updateProduct("1001", capture(slot)) }

        // String kosong dikirim, bukan diubah menjadi null, supaya deskripsi
        // benar-benar boleh dikosongkan.
        assertEquals("", slot.captured.description)
    }

    @Test
    fun `product with a blank description drops it`() = runTest {
        coEvery { api.createProduct(any()) } returns Response.success(
            EnvelopeDto(data = productDto(description = null))
        )

        repository.createProduct(
            name = "Air Galon",
            description = "   ",
            price = "18000.00",
            availability = true
        )

        val slot = slot<CreateOwnerProductRequestDto>()
        coVerify(exactly = 1) { api.createProduct(capture(slot)) }

        assertNull(slot.captured.description)
    }

    @Test
    fun `disabling a product succeeds even though the backend sends no data`() = runTest {
        coEvery { api.deleteProduct(any()) } returns Response.success(
            EnvelopeDto<OwnerProductDto>(data = null)
        )

        val result = repository.deleteProduct("1001")

        // Backend membalas `data: null`; keberhasilan dinilai dari status HTTP.
        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { api.deleteProduct("1001") }
    }

    @Test
    fun `forbidden delete is reported as a failure`() = runTest {
        coEvery { api.deleteProduct(any()) } returns Response.error(
            FORBIDDEN_CODE,
            ERROR_BODY.toResponseBody()
        )

        val result = repository.deleteProduct("1001")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    // --- Fixtures ---

    private fun productDto(
        id: Long = 1001L,
        name: String = "Air Galon",
        description: String? = "Air galon isi 19 liter.",
        price: String = "18000.00",
        isAvailable: Boolean = true
    ) = OwnerProductDto(
        id = id,
        name = name,
        description = description,
        price = price,
        is_available = isAvailable
    )

    private fun productPage(items: List<OwnerProductDto> = listOf(productDto())) = PageDto(
        data = items,
        meta = PageMetaDto(current_page = 1, per_page = 20, total = items.size)
    )

    private fun orderDto(
        orderStatus: String = "MENUNGGU_DIPROSES",
        assignment: OrderAssignmentDto? = null
    ) = OwnerOrderDto(
        id = 1001L,
        order_number = "ORD-20260930-0001",
        customer_id = 10L,
        items = emptyList(),
        subtotal_amount = "16000.00",
        delivery_fee = "5000.00",
        total_amount = "21000.00",
        order_status = orderStatus,
        payment = null,
        assignment = assignment
    )

    private fun orderDtoWithAssignment() = orderDto(
        orderStatus = "DITUGASKAN",
        assignment = OrderAssignmentDto(
            id = 77L,
            courier_id = 20L,
            status = "ASSIGNED",
            assigned_at = "2026-09-30T10:00:00Z"
        )
    )

    private fun orderPage(items: List<OwnerOrderDto> = listOf(orderDto())) = PageDto(
        data = items,
        meta = PageMetaDto(current_page = 1, per_page = 20, total = items.size)
    )

    private fun pendingPage(
        items: List<OwnerPendingPaymentDto> = listOf(
            OwnerPendingPaymentDto(
                id = 7001L,
                order_id = 1001L,
                order_number = "ORD-20260930-0001",
                payment_method = "QRIS",
                payment_status = "WAITING_VERIFICATION",
                amount = "21000.00",
                proof = PaymentProofDto(available = true)
            )
        )
    ) = PageDto(
        data = items,
        meta = PageMetaDto(current_page = 1, per_page = 20, total = items.size)
    )

    private fun paymentDto(
        paymentStatus: String,
        verifiedBy: Long? = null
    ) = CustomerPaymentDto(
        id = 7001L,
        order_id = 1001L,
        payment_method = "QRIS",
        payment_status = paymentStatus,
        amount = "21000.00",
        verified_by = verifiedBy
    )

    private fun String.toResponseBody(): ResponseBody =
        this.toResponseBody("application/json".toMediaType())

    companion object {
        private const val CONFLICT_CODE = 409
        private const val UNPROCESSABLE_CODE = 422
        private const val FORBIDDEN_CODE = 403
        private const val ERROR_BODY = """{"message":"Permintaan ditolak."}"""
    }
}
