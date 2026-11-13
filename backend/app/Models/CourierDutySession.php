<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

/**
 * Satu sesi bertugas (on-duty) kurir.
 *
 * Sesi aktif adalah sesi dengan `ended_at` NULL. Aturan "hanya satu sesi aktif
 * per kurir" ditegakkan `CourierDutyController` di dalam transaksi, bukan di
 * database, supaya berlaku sama pada MySQL dan SQLite.
 */
class CourierDutySession extends Model
{
    protected $fillable = ['courier_id', 'started_at', 'ended_at'];

    protected $casts = [
        'started_at' => 'datetime',
        'ended_at' => 'datetime',
    ];

    public function courier()
    {
        return $this->belongsTo(Courier::class);
    }

    public function locations()
    {
        return $this->hasMany(CourierDutyLocation::class, 'courier_duty_session_id');
    }

    /**
     * Lokasi terbaru untuk sesi ini. Deterministik lewat `recorded_at` sebagai
     * pengurutan dan hanya memuat satu baris.
     */
    public function latestLocation()
    {
        return $this->hasOne(CourierDutyLocation::class, 'courier_duty_session_id')
            ->latestOfMany('recorded_at');
    }

    public function scopeActive(Builder $query): Builder
    {
        return $query->whereNull('ended_at');
    }

    public function isActive(): bool
    {
        return $this->ended_at === null;
    }
}
