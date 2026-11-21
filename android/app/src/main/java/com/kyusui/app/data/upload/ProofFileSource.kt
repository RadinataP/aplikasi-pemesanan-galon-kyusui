package com.kyusui.app.data.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.kyusui.app.core.ProofUploadPolicy
import com.kyusui.app.domain.model.ProofImageSelection
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber file multipart untuk upload gambar.
 *
 * Interface dipisahkan agar repository dapat diuji tanpa ContentResolver Android.
 */
interface ProofFileSource {
    /** Membuat multipart untuk bukti pembayaran customer (field `proof_image`). */
    fun createProofPart(selection: ProofImageSelection): MultipartBody.Part

    /**
     * Membuat multipart dengan nama field sesuai endpoint.
     * Owner QRIS menggunakan `qris_image`; bukti pembayaran menggunakan `proof_image`.
     */
    fun createImagePart(
        fieldName: String,
        fileName: String,
        selection: ProofImageSelection
    ): MultipartBody.Part

    /** Mengambil metadata file yang dipilih pengguna, atau null jika tidak valid. */
    fun readSelection(uri: Uri): ProofImageSelection?
}

/**
 * Implementasi ContentResolver untuk gambar yang dipilih dari galeri.
 * MIME type dan ekstensi multipart ditentukan dari signature byte gambar,
 * bukan hanya metadata URI atau nama file yang bisa tidak akurat.
 */
@Singleton
class ContentResolverProofFileSource @Inject constructor(
    private val contentResolver: ContentResolver
) : ProofFileSource {

    override fun createProofPart(selection: ProofImageSelection): MultipartBody.Part =
        createImagePart(
            fieldName = PROOF_FIELD_NAME,
            fileName = DEFAULT_DISPLAY_NAME,
            selection = selection
        )

    override fun createImagePart(
        fieldName: String,
        fileName: String,
        selection: ProofImageSelection
    ): MultipartBody.Part {
        require(fieldName.isNotBlank()) { "Nama field upload tidak boleh kosong." }

        val uri = Uri.parse(selection.localUri)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("File gambar tidak dapat dibaca dari perangkat.")

        if (bytes.isEmpty()) {
            throw IllegalStateException("File gambar kosong.")
        }

        // Jangan mempercayai ekstensi/nama file atau MIME dari picker saja.
        val detected = detectSupportedImage(bytes)
            ?: throw IllegalStateException("Format gambar tidak didukung. Gunakan JPG, JPEG, atau PNG.")

        val baseName = fileName
            .substringAfterLast('/')
            .substringBeforeLast('.', fileName.substringAfterLast('/'))
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .ifBlank { "qris-image" }
        val safeFileName = "$baseName.${detected.extension}"
        val requestBody = bytes.toRequestBody(detected.mediaType)

        return MultipartBody.Part.createFormData(
            name = fieldName,
            filename = safeFileName,
            body = requestBody
        )
    }

    override fun readSelection(uri: Uri): ProofImageSelection? {
        val (size, name) = queryMetadata(uri)
        if (size <= 0L) return null

        val reportedMime = normalizeMimeType(contentResolver.getType(uri))
        // Reject unsupported known MIME types. Some document providers return null;
        // in that case the upload step verifies the actual file signature.
        if (reportedMime != null && reportedMime !in ProofUploadPolicy.ALLOWED_MIME_TYPES) {
            return null
        }

        val fallbackMime = reportedMime ?: mimeFromExtension(name)
        if (fallbackMime != null && fallbackMime !in ProofUploadPolicy.ALLOWED_MIME_TYPES) {
            return null
        }

        return ProofImageSelection(
            localUri = uri.toString(),
            // Jika metadata MIME tidak tersedia, simpan kandidat yang valid; signature
            // file tetap diverifikasi ulang sebelum request multipart dibuat.
            mimeType = fallbackMime ?: "image/jpeg",
            sizeBytes = size,
            displayName = name
        )
    }

    private fun queryMetadata(uri: Uri): Pair<Long, String> {
        var size = 0L
        var name = DEFAULT_DISPLAY_NAME

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name = it }
                }
            }
        }

        // Sebagian content provider tidak melaporkan ukuran. Coba ukur dari stream.
        if (size <= 0L) {
            size = runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                    }
                    total
                } ?: 0L
            }.getOrDefault(0L)
        }

        return size to name
    }

    private fun normalizeMimeType(mimeType: String?): String? {
        val normalized = mimeType?.lowercase()?.substringBefore(';') ?: return null
        return when (normalized) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg"
            "image/png" -> "image/png"
            else -> normalized
        }
    }

    private fun mimeFromExtension(fileName: String): String? = when (
        fileName.substringAfterLast('.', "").lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> null
    }

    private fun detectSupportedImage(bytes: ByteArray): DetectedImage? {
        // JPEG signature: FF D8 FF
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return DetectedImage("jpg", "image/jpeg".toMediaType())
        }

        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        if (bytes.size >= pngSignature.size &&
            pngSignature.indices.all { bytes[it] == pngSignature[it] }
        ) {
            return DetectedImage("png", "image/png".toMediaType())
        }

        return null
    }

    private data class DetectedImage(
        val extension: String,
        val mediaType: MediaType
    )

    companion object {
        const val PROOF_FIELD_NAME = "proof_image"
        const val DEFAULT_DISPLAY_NAME = "qris-proof.jpg"
        const val QRIS_IMAGE_FIELD_NAME = "qris_image"
        const val QRIS_IMAGE_DISPLAY_NAME = "active-qris.png"
    }
}