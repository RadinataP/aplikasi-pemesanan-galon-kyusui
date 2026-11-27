<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

// Only trusted server operators should have Artisan access. List output contains no contact data.
Artisan::command('berkah:requests {--status=pending}', function () {
    $status = $this->option('status');
    if (!in_array($status, ['pending', 'reviewing', 'completed', 'rejected'], true)) { $this->error('Status tidak valid.'); return 1; }
    $rows = \Illuminate\Support\Facades\DB::table('public_service_requests')->where('status', $status)->orderBy('created_at')->limit(100)->get(['id','type','status','created_at']);
    $this->table(['ID','Jenis','Status','Tanggal'], $rows->map(fn ($row) => (array) $row)->all());
})->purpose('Daftar permintaan publik (maksimum 100 per status)');
Artisan::command('berkah:request {id}', function () {
    $row = \Illuminate\Support\Facades\DB::table('public_service_requests')->where('id', $this->argument('id'))->first();
    if (!$row) { $this->error('Permintaan tidak ditemukan.'); return 1; }
    $this->line('Jenis: '.$row->type.' | Status: '.$row->status);
    // JSON encoding avoids terminal-control injection by untrusted form data.
    $this->line('Email: '.json_encode(\Illuminate\Support\Facades\Crypt::decryptString($row->email), JSON_THROW_ON_ERROR));
    $this->line('Pesan: '.json_encode($row->message ? \Illuminate\Support\Facades\Crypt::decryptString($row->message) : '', JSON_THROW_ON_ERROR));
})->purpose('Lihat satu permintaan secara privat');
Artisan::command('berkah:request-status {id} {status}', function () {
    $status = $this->argument('status');
    if (!in_array($status, ['reviewing','completed','rejected'], true)) { $this->error('Status tidak valid.'); return 1; }
    if (!$this->confirm('Konfirmasi: status ini dicatat setelah pemeriksaan operator; perintah tidak menghapus akun.')) return 1;
    $count = \Illuminate\Support\Facades\DB::table('public_service_requests')->where('id', $this->argument('id'))->update(['status'=>$status,'updated_at'=>now()]);
    if (!$count) { $this->error('Permintaan tidak ditemukan.'); return 1; }
    $this->info('Status diperbarui.');
})->purpose('Catat status setelah penanganan manual');
