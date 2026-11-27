# Panduan deployment dan release APKPure

## 1. Backend
Cadangkan database dan file storage production secara privat. Terapkan perubahan dari folder backend ke project asli, bukan mengganti data atau kredensial server. Pertahankan `.env` production dan APP_KEY asli; jangan gunakan `key:generate` pada instalasi yang sudah berjalan. Jangan copy file cache lama. Jika instalasi baru, buat `.env` dari `.env.example` lalu atur APP_KEY, database, HTTPS, email, dan kredensial yang tepat. Pastikan root web server adalah `public/`.

Pada staging, install dependency dengan Composer dan Node yang sesuai project, kemudian:

```sh
composer install
php artisan optimize:clear
php artisan migrate
php artisan test --filter=PublicServiceRequestTest
php artisan test
```

Gunakan database uji terpisah untuk pengujian. Jika semua lulus, di production gunakan deployment sesuai hosting, `composer install --no-dev --optimize-autoloader`, `php artisan optimize:clear`, `php artisan migrate --force` dan `php artisan config:cache`. Pastikan APP_ENV=production, APP_DEBUG=false, SESSION_SECURE_COOKIE=true, session/cache database berjalan, APP_URL sesuai domain. Jangan jalankan seeder demo. Seeder kini berhenti setelah role dibuat bila bukan local/testing.

Cek URL `https://depot.berkahwater.my.id/`, `/privacy-policy`, `/terms-and-conditions`, `/support`, `/delete-account`, dan `/manual-book`. Uji submit form dengan data dummy dan cek receipt. Route form memakai CSRF serta throttle per IP; pastikan konfigurasi reverse proxy tidak mempercayai header palsu. Lakukan evaluasi abuse rate-limit di production.

## 2. Operasional bantuan dan penghapusan akun

```sh
php artisan berkah:requests
php artisan berkah:request ID-TIKET
php artisan berkah:request-status ID-TIKET reviewing
php artisan berkah:request-status ID-TIKET completed
```

Status `rejected` juga tersedia. Perintah status hanya mencatat status, tidak menghapus akun. Akses Artisan terbatas operator terpercaya. Email/pesan tiket dienkripsi menggunakan APP_KEY, jangan bagikan output perintah. Tetapkan operator yang memeriksa antrean secara berkala dan menghubungi pengguna. Verifikasi kepemilikan akun melalui kanal aman sebelum tindakan; jangan pernah meminta OTP/kata sandi. Periksa pesanan/pembayaran aktif, kewajiban penyimpanan data dan relasi FK. Untuk permintaan sah, cabut token perangkat dan token sesi, nonaktifkan akun bila sesuai, lalu hapus/anonymisasi data sesuai prosedur yang telah disetujui pemilik dan kewajiban hukum. Jangan lakukan hard-delete tanpa audit hubungan transaksi, bukti pembayaran, histori lokasi dan backup. Konfirmasikan hasil kepada pengguna dan catat status sesuai tindakan sebenarnya. Tentukan retensi dan pembersihan tiket termasuk cadangan. Implementasi ini tidak menyediakan penghapusan otomatis atau verifikasi email otomatis.

## 3. Android
Buka folder `android/` di Android Studio, sync Gradle (JDK 17, SDK 34 sesuai project). Android Studio membuat `local.properties`. Jika ingin FCM, tambahkan `app/google-services.json` dari proyek Firebase untuk `com.kyusui.app`; plugin Google Services diterapkan bila file tersedia. Konfigurasi backend FCM_PROJECT_ID dan FCM_CREDENTIALS secara privat sesuai FcmPushNotificationService. Uji notifikasi. Bila belum aktif jangan mengiklankan push notification.

Jalankan pada Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest
```

Buat/ gunakan keystore resmi pemilik di Android Studio: Build → Generate Signed Bundle / APK → APK → pilih keystore → release. Jika sudah pernah distribusi, pertahankan signing key lama agar update kompatibel. Simpan backup key offline. Alternatif: salin `key.properties.example` menjadi `key.properties` secara lokal, isi path dan password sendiri, lalu ` .\gradlew.bat :app:assembleRelease ` (tanpa spasi pembungkus). Jangan commit key.properties, keystore, atau password.

Output biasanya `app/build/outputs/apk/release/app-release.apk` atau lokasi output Android Studio. Jangan upload `app-release-unsigned.apk` atau `app-debug.apk`. Gunakan Android SDK build-tools `apksigner`:

```powershell
& 'C:\path\to\Android\Sdk\build-tools\VERSION\apksigner.bat' verify --verbose --print-certs 'C:\path\to\app-release.apk'
```

Verifikasi sertifikat sesuai key release milikmu, bukan debug. Cek package/version dan flag debuggable di APK Analyzer Android Studio. Jika kode versi 1 pernah dirilis, naikkan versionCode. Uji fresh install dan update (APK debug lama mungkin perlu di-uninstall; backup data dahulu). Cek login/daftar, OTP, customer/owner/courier, pesanan, QRIS/tunai, GPS foreground serta penghentian tracking, notifikasi, link legal, tiket, jaringan putus dan instalasi di Android 7+ termasuk perangkat baru.

## 4. Distribusi
Setelah hasil verifikasi lulus, salin signed release APK ke `backend/public/downloads/berkah-water-release.apk` di deployment. Hapus file APK debug lama `public/downloads/Berkah Water.apk` dari server (backup privat jika diperlukan). Pastikan unduhan baru berfungsi dan hash SHA-256 file unduhan identik dengan APK yang telah diverifikasi. File baru tidak otomatis dianggap signed oleh website; hanya operator boleh menempatkan release yang sudah diverifikasi. Jangan menggunakan script Build-BerkahWater.ps1 untuk produksi karena itu khusus debug.

## 5. APKPure
Add application: `com.kyusui.app`, `Berkah Water`. Isi listing dari publication-assets, identitas pengelola dan email resmi. Upload ikon dan screenshot asli dari akun dummy. Sesuaikan persyaratan ukuran/format pada console. Gunakan tautan legal yang telah aktif. Upload APK release yang sama, isi versi, kategori, rating sesuai kuesioner, dan submit review. Periksa hasil review dari console. Tidak ada jaminan persetujuan.

## 6. Keamanan sebelum publish
Arsip awal mengandung .env, cache konfigurasi, log, dan bukti transaksi. Simpan secara privat, hapus salinan yang tidak diperlukan sesuai kebijakan, dan rotasi kredensial yang benar-benar terekspos dengan prosedur aman. APP_KEY lama harus dipertahankan atau dimigrasikan dengan rencana khusus. Audit akun demo yang mungkin terlanjur dibuat di production; nonaktifkan sesuai pemeriksaan. Pastikan kredensial Firebase server, data transaksi dan storage privat tidak disajikan oleh web server. Lakukan pengujian akses antar-role. Jangan memakai data nyata untuk screenshot.
