package com.kyusui.app.core

/**
 * Batas konfigurasi untuk seluruh interval tracking.
 *
 * ## UNRESOLVED TRACKING INTERVAL
 *
 * Specification tracking (docs 09) tidak menetapkan angka interval sebagai
 * business requirement. `UD-TRACK-01` (interval pengiriman lokasi kurir) dan
 * `UD-TRACK-02` (strategi pembacaan tracking customer) keduanya berstatus
 * `UNRESOLVED`, sama seperti `MINIMUM_ACCURACY` dan `LOCATION_RETENTION_PERIOD`.
 * Docs 09 juga menyatakan eksplisit bahwa angka final tidak boleh ditetapkan
 * hanya berdasarkan asumsi.
 *
 * Karena itu angka di sini BUKAN keputusan product. Angka ini adalah:
 *
 *  - satu-satunya tempat di aplikasi yang menyimpan nilai interval, supaya tidak
 *    ada literal interval yang tersebar di ViewModel atau Composable;
 *  - angka sementara yang ditandai sebagai belum disetujui lewat [IS_UNRESOLVED],
 *    sehingga layar bisa memberi tahu ke pengguna dan ke tim.
 *
 * ViewModel tidak membaca objek ini secara langsung. Interval masuk lewat
 * [TrackingSchedule] yang diinjeksi, karena Hilt tidak bisa mengisi parameter
 * bertipe primitif yang punya nilai default. Setelah `UD-TRACK-01` dan
 * `UD-TRACK-02` dikunci, hanya file ini yang perlu diubah.
 *
 * ## Batas bawah yang dipaksakan backend
 *
 * Backend adalah satu-satunya sumber kebenaran untuk batas bawah, jadi nilai di
 * bawah dibaca dari `backend/routes/api.php` dan bukan dipilih bebas:
 *
 *  - `GET orders/{order}/tracking` berada di dalam middleware
 *    `['role:CUSTOMER', 'throttle:5,1']`. Itu 5 permintaan per 60 detik,
 *    sehingga interval customer TIDAK BOLEH lebih cepat dari 12 detik.
 *    Melewati batas itu menghasilkan HTTP 429, bukan data.
 *    [CUSTOMER_READ_MINIMUM_MS] mengunci angka itu dan
 *    [CUSTOMER_READ_INTERVAL_MS] memberi ruang di atasnya.
 *
 *  - `POST courier/assignments/{assignment}/location` berada di group
 *    `prefix('courier')->middleware('ensure-role:COURIER')` yang TIDAK memakai
 *    `throttle`. Jadi backend tidak membatasi laju pengiriman lokasi kurir.
 *    [COURIER_SUBMISSION_INTERVAL_MS] sepenuhnya bergantung pada Considerations
 *    baterai, jaringan, dan beban database, dan justru itu yang belum dikunci.
 */
object TrackingIntervals {

    /**
     * Interval minimum absolut untuk pembacaan tracking customer, dalam milidetik.
     *
     * Turunan langsung dari `throttle:5,1` pada
     * `GET orders/{order}/tracking`: 60.000 / 5 = 12.000 ms.
     *
     * Ini bukan preferensi, ini syarat agar permintaan tidak ditolak 429.
     * Jangan diturunkan tanpa mengubah middleware backend lebih dulu.
     */
    const val CUSTOMER_READ_MINIMUM_MS: Long = 12_000L

    /**
     * Interval polling tracking untuk customer, dalam milidetik.
     *
     * UNRESOLVED TRACKING INTERVAL - `UD-TRACK-02` belum dikunci.
     *
     * Nilai 15 detik menghasilkan 4 permintaan per menit, jadi masih di bawah
     * batas 5 permintaan per menit dengan sisa satu permintaan untuk aksi
     * manual pengguna. Pilihan yang belum diputuskan adalah polling tetap,
     * polling adaptif, atau mekanisme lain; docs 09 menyatakan WebSocket bukan
     * komponen wajib.
     */
    const val CUSTOMER_READ_INTERVAL_MS: Long = 15_000L

    /**
     * Interval pengiriman posisi kurir ke backend, dalam milidetik.
     *
     * UNRESOLVED TRACKING INTERVAL - `UD-TRACK-01` belum dikunci.
     *
     * Backend tidak membatasi laju ini, jadi angka 30 detik sepenuhnya
     * merupakan hasil trade-off yang diturunkan docs 09 dan belum disepakati:
     * frekuensi pembaruan, konsumsi baterai, responsivitas tracking, penggunaan
     * jaringan, dan beban server serta database. Docs 09 juga meminta
     * acquisition dihentikan segera setelah tracking selesai, dan itu diterapkan
     * lewat [com.kyusui.app.ui.courier.tracking.CourierTrackingViewModel], bukan
     * lewat interval yang lebih kecil.
     */
    const val COURIER_SUBMISSION_INTERVAL_MS: Long = 30_000L

    /**
     * Menandai apakah konfigurasi interval ini masih menunggu
     * `UD-TRACK-01` dan `UD-TRACK-02`.
     *
     * Keadaan ini ditampilkan di layar agar angka di sini tidak terbaca sebagai
     * angka yang sudah disetujui. Setelah spesifikasi mengunci angka, nilai ini
     * berubah menjadi `false` di satu tempat.
     */
    const val IS_UNRESOLVED: Boolean = true

    /** Keterangan singkat untuk ditampilkan di UI, dalam Bahasa Indonesia. */
    const val UNRESOLVED_NOTICE: String =
        "Interval tracking belum ditetapkan specification (UD-TRACK-01, UD-TRACK-02). " +
            "Nilai sementara dipakai dan dapat berubah."
}