<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Sesi bertugas kurir (on-duty) dan histori lokasinya.
 *
 * Fitur ini sengaja dipisahkan dari `courier_assignments`/`courier_locations`.
 * Kontrak yang sudah berjalan (tracking customer) membaca lokasi dari
 * `courier_locations` yang selalu terikat pada satu assignment aktif; menjadikan
 * `courier_assignment_id` nullable akan mengubah kontrak itu. Karena itu lokasi
 * tanpa assignment disimpan di tabel sendiri dengan lifecycle sendiri.
 *
 * Aturan yang dijaga di level aplikasi (`CourierDutyController`), bukan di
 * database, karena MySQL dan SQLite berbeda soal partial index:
 * - hanya boleh ada satu sesi aktif (`ended_at` NULL) per kurir.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('courier_duty_sessions', function (Blueprint $t) {
            $t->id();
            $t->foreignId('courier_id')->constrained()->cascadeOnDelete();
            $t->timestamp('started_at');
            $t->timestamp('ended_at')->nullable();
            $t->timestamps();
            $t->index(['courier_id', 'ended_at']);
        });

        Schema::create('courier_duty_locations', function (Blueprint $t) {
            $t->id();
            $t->foreignId('courier_duty_session_id')->constrained('courier_duty_sessions')->cascadeOnDelete();
            $t->foreignId('courier_id')->constrained()->restrictOnDelete();
            $t->decimal('latitude', 10, 7);
            $t->decimal('longitude', 10, 7);
            $t->decimal('accuracy_meters', 8, 2);
            $t->timestamp('recorded_at');
            $t->index(['courier_duty_session_id', 'recorded_at'],
                'courier_duty_locations_session_recorded_at_index');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('courier_duty_locations');
        Schema::dropIfExists('courier_duty_sessions');
    }
};
