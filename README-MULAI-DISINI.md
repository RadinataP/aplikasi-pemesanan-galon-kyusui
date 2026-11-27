# KYŪSUI — Berkah Water

Panduan awal pengembangan, pengujian, dan distribusi sistem pemesanan serta pengantaran air galon Berkah Water. KYŪSUI adalah nama proyek/repository; Berkah Water adalah nama aplikasi yang digunakan untuk distribusi.

## 1. Identitas proyek

| Informasi | Keterangan |
| --- | --- |
| Repository | [RadinataP/aplikasi-pemesanan-galon-kyusui](https://github.com/RadinataP/aplikasi-pemesanan-galon-kyusui) |
| Branch utama | `main` |
| Nama aplikasi | Berkah Water |
| Application ID Android | `id.my.berkahwater.depot` |
| Namespace Kotlin | `com.kyusui.app` (bukan identitas distribusi) |
| Backend | Laravel, PHP, MySQL |
| Android | Kotlin, Jetpack Compose |
| JDK yang digunakan | Eclipse Temurin 17.0.11 |
| Gradle Wrapper yang teruji secara lokal | 8.14.6 |
| Website | <https://depot.berkahwater.my.id/> |
| API | <https://api.berkahwater.my.id/api/v1/> |
| Health check | <https://api.berkahwater.my.id/up> |
| Akses server sementara | Cloudflare Named Tunnel menuju backend lokal |

Nilai `versionCode`, `versionName`, `minSdk`, `targetSdk`, dan `compileSdk` mengikuti konfigurasi Gradle aktual. Periksa juga metadata APK yang akan didistribusikan; jangan menggunakan nilai versi lama dari arsip persiapan sebagai sumber utama.

## 2. Fitur sistem

- Pelanggan: registrasi, login, pemulihan kata sandi melalui OTP, pemesanan galon, pembayaran QRIS atau tunai, status dan riwayat pesanan, serta pelacakan pengantaran.
- Pemilik depot: pengelolaan pesanan dan pemantauan operasional.
- Kurir: penugasan, detail pengantaran, lokasi pelanggan, dan pembaruan lokasi selama proses pengiriman.
- Website: pengenalan layanan, unduhan aplikasi, panduan pengguna, kebijakan privasi, syarat dan ketentuan, bantuan, dan permintaan penghapusan akun.

Permintaan penghapusan akun ditangani melalui proses operator. Pencatatan status tiket bukan penghapusan akun otomatis. Aktivasi dan pengujian notifikasi FCM perlu dikonfirmasi sebelum fitur tersebut diklaim aktif.

## 3. Struktur proyek

| Lokasi | Kegunaan |
| --- | --- |
| `backend/` | Laravel REST API, website, migrasi, dan pengujian backend |
| `android/` | Source Android, Gradle, resource, dan unit test |
| `android/key.properties.example` | Contoh konfigurasi signing tanpa kredensial asli |
| `android/gradle/properties.local.example` | Contoh konfigurasi lokal |
| `publication-assets/` | Aset branding dan materi listing APKPure |
| `Berkah_Water_Screenshot_APKPure/` | Screenshot publikasi, hanya untuk data yang aman dipublikasikan |
| `PANDUAN-RELEASE.md` | Panduan pengujian, deployment, signing, dan distribusi |

APK, keystore, file konfigurasi rahasia, data pelanggan, log produksi, dan cadangan database tidak merupakan bagian dari source repository.

## 4. Mulai pengembangan

1. Clone repository dengan akun yang memiliki akses. Gunakan folder yang sudah ada apabila project telah di-clone.
2. Siapkan PHP, Composer, MySQL, Android Studio, Android SDK sesuai Gradle, dan JDK 17.
3. Untuk instalasi backend baru, buat `.env` dari `.env.example`, isi konfigurasi database pengembangan, install dependency, buat APP_KEY baru khusus instalasi tersebut, dan jalankan migrasi pada database pengembangan. Jangan menimpa `.env` atau APP_KEY instalasi yang sudah berjalan.
4. Buka folder `android/` pada Android Studio, pilih Gradle JDK 17 dan lakukan Gradle Sync. Buat konfigurasi lokal sesuai lingkungan pengembangan dan contoh yang tersedia.
5. Untuk menggunakan API publik, pastikan URL dan jaringan benar. Gunakan akun dummy serta lingkungan pengujian terpisah untuk operasi yang mengubah data.

Lihat [PANDUAN-RELEASE.md](PANDUAN-RELEASE.md) untuk perintah Windows, pengujian, dan prosedur release.

## 5. Alur kerja GitHub

| Branch | Ruang lingkup | Status berdasarkan log 28 November 2026 (WIB) |
| --- | --- | --- |
| `feature/backend` | API, layanan publik, dan pengamanan konfigurasi | PR #13 dan #14 merged |
| `feature/ui-ux` | Branding dan launcher icon | PR #15 merged |
| `test/quality-assurance` | Unit test Android | PR #16 merged |
| `docs/project-management` | Dokumentasi dan persiapan release | PR #17 merged |
| `feature/release-integration` | Integrasi akhir Android dan dokumentasi | Sedang dikerjakan pada verifikasi terakhir |

Gunakan Pull Request ke `main`, periksa diff, jalankan pengujian yang sesuai, dan pertahankan atribusi commit sesuai pembuat pekerjaan sebenarnya. Jangan menghapus branch jika masih diperlukan untuk penelusuran riwayat proyek.

## 6. Status pengujian dan publikasi

Snapshot berdasarkan hasil terminal dan console yang dilaporkan hingga 28 November 2026:

- Android `testDebugUnitTest`: **BUILD SUCCESSFUL**, 33 Gradle task dijalankan, durasi 7 menit 26 detik, dengan JDK 17.0.11 dan Gradle 8.14.6. Angka 33 adalah jumlah task Gradle, bukan jumlah test case.
- Hasil tersebut berasal dari working tree lokal yang sudah memuat perubahan Android terbaru, bukan bukti pengujian otomatis terhadap seluruh isi `main` di GitHub.
- Signed release APK telah dibangun dan disalin ke folder unduhan website dengan nama `Berkah Water.apk`.
- Pengajuan APKPure terakhir diketahui dalam proses review; persetujuan dan publikasi final belum terkonfirmasi.
- Final testing Android setelah integrasi, hasil lengkap pengujian Laravel pada database terpisah, pengujian end-to-end, dan status GitHub Actions masih perlu diverifikasi.

## 7. Keamanan dan operasional

- Jangan commit `.env`, `local.properties`, `key.properties`, keystore, token Cloudflare, private key, atau konfigurasi rahasia lainnya.
- Jangan menjalankan `migrate:fresh`, `db:wipe`, seeder demo, atau `key:generate` pada database/instalasi produksi yang aktif.
- Gunakan akun dummy untuk screenshot dan pengujian. Simpan log, data transaksi, dan backup secara privat.
- Server sementara memerlukan komputer, internet, MySQL, Laravel, queue worker jika digunakan, dan Cloudflare Tunnel yang aktif. Monitor boleh mati, tetapi komputer tidak boleh masuk Sleep selama layanan diperlukan.
- `php artisan serve` merupakan development server, bukan web server produksi untuk operasional resmi jangka panjang.
- Periksa tiket bantuan dan penghapusan akun melalui operator yang berwenang. Pastikan identitas pengelola, kontak resmi, serta kebijakan retensi data sesuai praktik nyata.

## 8. Tautan layanan

- [Website Berkah Water](https://depot.berkahwater.my.id/)
- [Kebijakan Privasi](https://depot.berkahwater.my.id/privacy-policy)
- [Syarat dan Ketentuan](https://depot.berkahwater.my.id/terms-and-conditions)
- [Bantuan](https://depot.berkahwater.my.id/support)
- [Permintaan Penghapusan Akun](https://depot.berkahwater.my.id/delete-account)
- [Panduan Pengguna](https://depot.berkahwater.my.id/manual-book)

Tautan di atas adalah konfigurasi layanan yang digunakan dalam proyek; ketersediaan real-time harus diperiksa saat deployment atau review.
