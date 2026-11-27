@extends('public-info.layout')
@section('title','Penghapusan Akun')
@section('intro','Ajukan permintaan penghapusan akun dan data terkait tanpa perlu memasang aplikasi.')
@section('content')
<h2>Proses permintaan</h2><ol><li>Masukkan email yang digunakan pada akun Berkah Water.</li><li>Simpan nomor referensi setelah pengiriman formulir.</li><li>Pengelola memverifikasi kepemilikan akun melalui komunikasi yang aman.</li><li>Setelah verifikasi dan pemeriksaan transaksi yang masih berjalan, pengelola memproses permintaan serta menjelaskan data yang perlu dipertahankan apabila ada kewajiban hukum atau transaksi yang belum selesai.</li></ol><p class="legal-notice">Pengiriman formulir tidak langsung menonaktifkan atau menghapus akun. Jangan memberikan kata sandi atau OTP kepada pihak lain.</p>
@include('public-info.form')
@endsection
