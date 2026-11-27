# Panduan Deployment dan Release Berkah Water

Dokumentasi operasional KYŪSUI untuk pengujian, deployment backend, signing Android, distribusi APK, dan review APKPure. Diperbarui berdasarkan progres yang dilaporkan hingga 28 November 2026 (WIB). Panduan ini tidak menyertakan kredensial produksi dan tidak menyatakan bahwa seluruh pengujian telah lulus.

## 1. Identitas release

| Komponen | Konfigurasi |
| --- | --- |
| Nama aplikasi | Berkah Water |
| Application ID | `id.my.berkahwater.depot` |
| Namespace Kotlin | `com.kyusui.app` |
| Website | <https://depot.berkahwater.my.id/> |
| REST API | <https://api.berkahwater.my.id/api/v1/> |
| Health check | <https://api.berkahwater.my.id/up> |
| Java | JDK 17.0.11 (Eclipse Temurin) |
| Gradle Wrapper | 8.14.6 |
| APK hasil build | `android/app/build/outputs/apk/release/app-release.apk` |
| Lokasi APK unduhan website | `backend/public/downloads/Berkah Water.apk` |
| Distribusi | Signed release APK |
| APKPure | Pengajuan terakhir diketahui sedang dalam review |

`applicationId` berbeda dari namespace Kotlin. Jangan mengganti seluruh deklarasi package Kotlin hanya untuk mengubah identitas distribusi. Nilai versi dan SDK harus mengikuti Gradle serta APK aktual.

## 2. Backend dan Cloudflare Tunnel

### 2.1. Persiapan deployment

Cadangkan database dan file storage secara privat sebelum melakukan perubahan. Pertahankan `.env` dan APP_KEY asli pada instalasi aktif. Jangan menjalankan `key:generate`, `migrate:fresh`, `db:wipe`, atau seeder demo pada produksi.

Untuk instalasi baru pada lingkungan pengembangan/staging, dari folder `backend/`:

```powershell
composer install
php artisan optimize:clear
php artisan migrate
```

Sebelum menjalankan migrasi, pastikan database tujuan benar dan konfigurasi tidak mengarah ke produksi. Apabila menggunakan aset frontend yang membutuhkan build, ikuti konfigurasi package manager pada repository.

Untuk deployment produksi yang sudah disetujui dan memiliki backup, gunakan prosedur hosting yang sesuai, install dependency produksi, tinjau migrasi, dan jalankan hanya setelah verifikasi staging:

```powershell
composer install --no-dev --optimize-autoloader
php artisan optimize:clear
php artisan migrate --force
php artisan config:cache
```

Atur `APP_ENV=production`, `APP_DEBUG=false`, cookie sesi aman melalui HTTPS, konfigurasi database/session/cache/queue yang sesuai, dan `APP_URL` sesuai deployment. Web root harus menunjuk ke `backend/public/`, bukan root repository. Periksa konfigurasi trusted proxy agar header dari klien tidak dipercaya secara sembarangan.

### 2.2. Operasional server sementara di Windows

Arsitektur saat ini menggunakan Cloudflare Named Tunnel ke Laravel dan MySQL lokal. Cloudflare Workers Builds merupakan integrasi berbeda, bukan mekanisme yang menjalankan Laravel lokal.

Jika server belum berjalan, aktifkan MySQL terlebih dahulu. Jalankan perintah berikut pada terminal terpisah dari folder `backend/`:

```powershell
php artisan serve --host=127.0.0.1 --port=8000
```

```powershell
php artisan queue:work --sleep=3 --tries=3
```

Untuk instalasi Cloudflare sebagai Windows Service, periksa dari PowerShell Administrator:

```powershell
Get-Service cloudflared
# Jalankan hanya apabila service belum aktif:
Start-Service cloudflared
```

Jangan menjalankan instance ganda jika Laravel, queue worker, atau tunnel sudah aktif. Periksa akses dari jaringan eksternal:

```powershell
curl.exe -I https://api.berkahwater.my.id/up
curl.exe -I https://depot.berkahwater.my.id/
```

Health check HTTP 200 tidak membuktikan seluruh fungsi aplikasi berjalan. Pastikan laptop tidak Sleep, koneksi internet stabil, dan database tidak dipublikasikan langsung. Untuk operasional resmi, gunakan web server produksi dan pengelolaan proses yang sesuai; `php artisan serve` hanya untuk pengembangan/pengujian sementara.

Jangan menghentikan server yang sedang diperlukan untuk review APKPure hanya untuk memperbarui dokumentasi atau GitHub. Jika Workers Builds tidak digunakan, tinjau integrasinya sebelum memutuskan koneksi Git; jangan menghapus DNS atau tunnel. Status pemutusan integrasi belum terkonfirmasi.

### 2.3. Pemeriksaan Laravel

Gunakan database testing terpisah, bukan database operasional. Periksa `phpunit.xml`, `.env.testing` apabila tersedia, dan variabel lingkungan yang dapat mengubah koneksi sebelum menjalankan test. Pastikan semua koneksi yang digunakan test mengarah ke lingkungan uji. Jangan tampilkan password atau isi `.env` ke publik.

```powershell
php -v
php artisan --version
composer check-platform-reqs
```

Setelah database testing dipastikan aman:

```powershell
php artisan test --filter=PublicServiceRequestTest
php artisan test
composer audit
```

Catat hasil aktual beserta commit dan tanggal pengujian. Hasil lengkap final testing Laravel belum dikonfirmasi pada pembaruan dokumen ini. Jika menggunakan `RefreshDatabase`, risiko terhadap database yang salah konfigurasi harus diperiksa secara khusus.

## 3. Android: konfigurasi dan pengujian

Buka folder `android/` di Android Studio dan pilih Gradle JDK 17. Gunakan Android SDK sesuai versi yang ditetapkan dalam project. Biarkan `local.properties` menyimpan konfigurasi SDK lokal; gunakan file contoh untuk acuan konfigurasi API apabila diperlukan. API produksi adalah `https://api.berkahwater.my.id/api/v1/`.

Jika FCM digunakan, konfigurasi Firebase Android harus terdaftar untuk application ID `id.my.berkahwater.depot`. File `app/google-services.json` harus sesuai proyek Firebase yang benar. Kredensial server Firebase dikelola secara privat melalui konfigurasi backend yang sesuai. Jangan mengklaim notifikasi aktif sebelum pengujian perangkat berhasil.

### 3.1. Pengujian dari Git Bash

Contoh berikut memakai JDK lokal yang telah berhasil digunakan pada komputer pengembangan. Sesuaikan lokasi instalasi, bukan source code project. Jalankan dari root repository:

```bash
export JAVA_HOME="$(cygpath -m /path/to/jdk17)"
export PATH="$(cygpath -u "$JAVA_HOME")/bin:$PATH"
hash -r
java -version
cd android
./gradlew.bat --version
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Ganti `/path/to/jdk17` dengan folder JDK 17 yang benar sebelum mengeksekusi. Di komputer pengembangan sebelumnya, JDK 17.0.11 berhasil mengatasi error Gradle ketika Java 25.0.4 aktif.

Alternatif dari PowerShell, setelah `JAVA_HOME` dan `PATH` mengarah ke JDK 17, jalankan dari folder `android/`:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Laporan HTML unit test debug biasanya tersedia pada `android/app/build/reports/tests/testDebugUnitTest/index.html`. Gunakan laporan aktual untuk mengetahui jumlah test case, kegagalan, dan durasi.

### 3.2. Catatan hasil pengujian

| Pengujian | Hasil yang diketahui |
| --- | --- |
| `testDebugUnitTest` pada working tree lokal | BUILD SUCCESSFUL, 33 task Gradle dijalankan, 7 menit 26 detik |
| JDK / Gradle | 17.0.11 / 8.14.6 |
| Warning kompilasi | API lama/deprecated; tidak menggagalkan build tersebut |
| Final test setelah integrasi dan `assembleDebug` | Belum terkonfirmasi |
| Unit test release, lint, dan end-to-end pada perangkat | Perlu diverifikasi |

Hasil pengujian sebelumnya bukan bukti bahwa semua test lulus pada commit akhir `main`. Jalankan ulang final testing, idealnya pada checkout bersih dari commit release yang akan digunakan.

## 4. Build dan verifikasi signed release APK

Signed release APK sudah dibuat sebelumnya dan digunakan untuk persiapan distribusi. Jangan membangun ulang, menimpa, atau mengubah APK yang sedang dalam review apabila tidak ada kebutuhan perubahan aplikasi.

Jika diperlukan release berikutnya:

1. Pertahankan application ID `id.my.berkahwater.depot` dan signing key yang sama untuk update aplikasi yang telah didistribusikan.
2. Periksa `versionCode` dan `versionName` pada `android/app/build.gradle.kts` serta versi yang telah diunggah. Naikkan `versionCode` untuk pembaruan resmi sesuai kebutuhan distribusi.
3. Gunakan Android Studio **Build > Generate Signed Bundle / APK**, atau konfigurasi `android/key.properties` lokal berdasarkan `android/key.properties.example`. Jangan commit file asli atau password.
4. Simpan keystore dan cadangannya secara privat di luar repository. Jangan membuat signing key baru untuk update yang memerlukan sertifikat lama.

Dari folder `android/` pada PowerShell:

```powershell
.\gradlew.bat :app:assembleRelease
```

Output standar proyek: `app/build/outputs/apk/release/app-release.apk`. Jangan menggunakan APK debug atau unsigned untuk distribusi resmi. Script `Build-BerkahWater.ps1` berasal dari paket persiapan debug; periksa implementasinya sebelum digunakan, dan jangan menganggap outputnya sebagai signed release.

Verifikasi APK menggunakan `apksigner` dari Android SDK (sesuaikan path):

```powershell
& 'C:\path\to\Android\Sdk\build-tools\VERSION\apksigner.bat' verify --verbose --print-certs '.\app\build\outputs\apk\release\app-release.apk'
```

Periksa sertifikat terhadap signing key release yang sah, application ID, versi, flag debuggable, dan SDK melalui APK Analyzer atau alat SDK yang tersedia. Simpan hasil verifikasi dan hash SHA-256 sebagai catatan release.

## 5. Distribusi melalui website

File unduhan saat ini menggunakan nama `backend/public/downloads/Berkah Water.apk`. Pertahankan nama ini selama route atau tautan website menggunakannya. Jangan menjalankan instruksi lama untuk menghapus file tersebut tanpa pengganti yang telah diverifikasi.

Untuk release baru yang telah disetujui, cadangkan APK lama secara privat, verifikasi APK baru, dan baru lakukan penggantian sesuai jadwal deployment. Dari root repository, pemeriksaan hash file lokal dapat dilakukan tanpa menimpa file:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'android/app/build/outputs/apk/release/app-release.apk'
Get-FileHash -Algorithm SHA256 -LiteralPath 'backend/public/downloads/Berkah Water.apk'
```

Jika keduanya dimaksudkan sebagai APK release yang sama, hash harus identik. Periksa pula hasil unduhan dari website melalui browser dan pastikan file yang tersedia benar-benar signed. APK tidak perlu di-commit ke repository source; jika ingin membagikannya melalui GitHub Releases, gunakan aset release yang sudah disetujui pemilik dan pastikan sesuai file distribusi resmi.

## 6. Publikasi APKPure

- Nama aplikasi: **Berkah Water**.
- Package/application ID: **`id.my.berkahwater.depot`**.
- Gunakan signed release APK dengan versi dan sertifikat yang sesuai.
- Gunakan ikon dan materi branding asli, screenshot dari akun dummy, deskripsi yang sesuai fitur nyata, kontak pengelola resmi, dan rating/kategori berdasarkan kuesioner serta fungsi aplikasi.
- Ikuti persyaratan aset dan formulir pada console APKPure yang berlaku saat pengajuan. Checklist lokal bukan pengganti validasi console.
- Status terakhir yang dilaporkan: pengajuan sedang direview. Periksa console untuk status terbaru. Tidak ada jaminan persetujuan atau tanggal publikasi.
- Jangan mengajukan aplikasi baru dengan package lama `com.kyusui.app` atau mengganti APK yang sedang diperiksa tanpa alasan yang diperlukan.

Materi publikasi tersedia di `publication-assets/` dan `Berkah_Water_Screenshot_APKPure/`. Jika `publication-assets/listing-apkpure.txt` masih memuat package lama atau metadata versi lama, perbarui berdasarkan APK aktual sebelum digunakan kembali.

Tautan yang perlu diuji dari jaringan eksternal:

| Layanan | URL |
| --- | --- |
| Website | <https://depot.berkahwater.my.id/> |
| Kebijakan Privasi | <https://depot.berkahwater.my.id/privacy-policy> |
| Syarat dan Ketentuan | <https://depot.berkahwater.my.id/terms-and-conditions> |
| Bantuan | <https://depot.berkahwater.my.id/support> |
| Penghapusan Akun | <https://depot.berkahwater.my.id/delete-account> |
| Manual pengguna | <https://depot.berkahwater.my.id/manual-book> |

## 7. Operasional bantuan dan penghapusan akun

Dari folder backend, operator berwenang dapat menggunakan perintah Artisan yang tersedia:

```powershell
php artisan berkah:requests
php artisan berkah:request ID-TIKET
php artisan berkah:request-status ID-TIKET reviewing
php artisan berkah:request-status ID-TIKET completed
```

Status `rejected` juga tersedia. Ganti `ID-TIKET` dengan ID yang sah. Perintah status hanya mencatat status, bukan menghapus akun secara otomatis. Jangan membagikan output tiket yang mengandung data pengguna.

Verifikasi kepemilikan akun melalui kanal aman tanpa meminta OTP atau password. Periksa transaksi aktif, kewajiban penyimpanan data, relasi database, dan dampak terhadap backup. Cabut token sesi/perangkat dan lakukan penonaktifan, penghapusan, atau anonimisasi hanya sesuai prosedur resmi yang telah disetujui. Konfirmasi hasil kepada pemohon sebelum mencatat status yang sesuai. Tentukan penanggung jawab antrean tiket, waktu tanggapan, serta retensi data. Jangan mengklaim ada penghapusan otomatis atau notifikasi tiket baru tanpa implementasi dan pengujian yang mendukungnya.

## 8. Checklist final testing

- [ ] Unit test Android dan build debug pada integrasi akhir berhasil.
- [ ] Pengujian Laravel dilakukan pada database testing terpisah dan hasilnya tercatat.
- [ ] Signed APK terverifikasi: application ID, versi, sertifikat, dan status non-debug.
- [ ] Fresh install dan update diuji pada perangkat Android yang sesuai.
- [ ] Login, registrasi, OTP, profil, dan pembatasan akses antar-role diuji.
- [ ] Pemesanan, verifikasi pembayaran QRIS/tunai, penugasan kurir, dan status pesanan diuji.
- [ ] Izin lokasi, tracking, penghentian berbagi lokasi, dan kondisi jaringan terputus diuji.
- [ ] Notifikasi diuji jika diaktifkan.
- [ ] Website, download APK, halaman legal, bantuan, dan tiket penghapusan akun diuji.
- [ ] Tidak ada data nyata pelanggan pada screenshot maupun materi publikasi.
- [ ] Status APKPure dan identitas developer diperiksa dari console.

Checklist ini adalah daftar verifikasi untuk release akhir, bukan klaim bahwa semua langkah telah selesai.

## 9. Audit repository dan GitHub Release

Sebelum commit, periksa file yang akan diunggah:

```bash
git status --short
git --no-pager diff --cached --stat
git --no-pager diff --cached --name-only
git diff --cached --check
```

Periksa bahwa konfigurasi lokal dan kredensial tidak dilacak:

```bash
git ls-files -- android/local.properties android/key.properties backend/.env
```

Tidak ada output adalah hasil yang diharapkan. Periksa juga isi perubahan, contoh konfigurasi, script, file sertifikat, dan screenshot. Pemeriksaan nama file tidak menjamin seluruh repository atau riwayat Git bebas rahasia. Jika terdapat kredensial yang pernah terekspos, tangani dengan rotasi yang terencana; jangan mengganti APP_KEY aktif tanpa strategi migrasi data terenkripsi.

Gunakan branch `feature/release-integration`, commit dengan atribusi asli, push, dan ajukan Pull Request ke `main`. Tinjau seluruh diff, hasil test, dan status merge sebelum membuat release. Pertahankan branch jika diperlukan untuk audit riwayat. Jangan gunakan `git add .`, force push, atau reset destruktif untuk menyelesaikan konflik lokal.

Setelah final integration di-merge dan pengujian commit akhir lulus, buat tag dan GitHub Release dengan versi yang cocok dengan APK aktual. Sertakan catatan perubahan, hasil pengujian yang benar-benar dilakukan, dan informasi distribusi yang disetujui. Jangan mengklaim GitHub Actions hijau apabila workflow belum tersedia atau belum berhasil.
