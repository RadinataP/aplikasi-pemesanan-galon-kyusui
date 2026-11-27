# Berkah Water — Paket persiapan APKPure

Paket ini berisi source lengkap hasil revisi (tanpa dependency terunduh, konfigurasi rahasia, APK debug, atau data production), aset listing, dan panduan deployment. Ini bukan APK final siap upload. Source asli tetap tidak diubah.

## Isi folder
- `backend/`: Laravel 13, landing page, route baru, formulir dan tabel permintaan publik, perintah operator, tes baru.
- `android/`: Kotlin/Jetpack Compose, tautan legal dan dukungan pada Settings dan Register, opsi signing release lokal, konfigurasi FCM bersyarat.
- `publication-assets/`: ikon asli 512 px, banner master, deskripsi APKPure dan checklist screenshot.
- `PANDUAN-RELEASE.md`: langkah deployment, signing, pengujian, dan penanganan permintaan.

## Yang harus dikonfirmasi pemilik sebelum publish
1. Nama resmi pengelola dan email kontak yang dipantau; isi developer profile APKPure dan finalisasi halaman legal. Kebijakan yang dibuat adalah draft berbasis source, bukan jaminan kepatuhan hukum. Pastikan periode retensi, pemroses data, mekanisme hak pengguna dan waktu tanggapan yang benar-benar dijalankan.
2. Operator yang memeriksa tiket bantuan dan penghapusan akun secara rutin. Permintaan disimpan dan status dikelola lewat Artisan, tetapi verifikasi kepemilikan dan penghapusan/anonymisasi data dilakukan manual dengan prosedur resmi. Tidak ada hapus permanen otomatis, dashboard admin atau notifikasi email tiket baru.
3. Firebase: masukkan konfigurasi proyek Firebase yang benar jika FCM dipakai, siapkan kredensial server privat, dan uji pengiriman.
4. Buat atau gunakan keystore milik sendiri, uji build dan instalasi, ambil screenshot nyata, kemudian upload.
5. Pastikan syarat APKPure dan Android developer verification yang berlaku saat publikasi; tidak diverifikasi langsung pada console dalam pekerjaan ini.

## Status verifikasi
- Source arsip dibuka dan diperiksa. PHP lint dijalankan pada app, routes, migrations dan tests.
- Tidak ada pengujian Laravel end-to-end atau Gradle build: runtime ini tidak memiliki Android SDK dan PHP extension SQLite/MBString yang diperlukan, serta tidak memiliki konfigurasi server asli.
- Tidak mengubah konfigurasi server, database production, DNS, atau akun APKPure.
- Tidak mencakup keystore, APK final, email developer yang belum diketahui, atau screenshot asli yang belum diambil.

**Jangan menyalin file `.env.example` ke atas `.env` produksi atau menjalankan `migrate:fresh`/`db:seed` pada production.** Simpan cadangan dan lakukan staging dahulu.
