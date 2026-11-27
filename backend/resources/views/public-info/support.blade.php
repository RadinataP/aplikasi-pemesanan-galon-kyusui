@extends('public-info.layout')
@section('title','Bantuan & Dukungan')
@section('intro','Sampaikan kendala akun, pesanan, pembayaran atau pertanyaan privasi kepada pengelola Berkah Water.')
@section('content')
<h2>Butuh bantuan?</h2><p>Sebelum mengirim permintaan, Anda dapat membaca <a href="/manual-book">panduan penggunaan</a>. Formulir berikut mencatat permintaan untuk ditinjau oleh pengelola. Simpan nomor referensi setelah pengiriman.</p>
@include('public-info.form')
@endsection
