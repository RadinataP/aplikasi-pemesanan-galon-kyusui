<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

/**
 * Histori lokasi kurir selama sesi bertugas, tanpa assignment.
 *
 * Bentuk kolomnya sengaja sama dengan `courier_locations`
 * (`docs/05_KYUSUI_DATABASE_SCHEMA_REBUILT.md` section 17) supaya
 * `CourierLocationResource` bisa dipakai untuk keduanya.
 */
class CourierDutyLocation extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'courier_duty_session_id',
        'courier_id',
        'latitude',
        'longitude',
        'accuracy_meters',
        'recorded_at',
    ];

    protected $casts = [
        'latitude' => 'decimal:7',
        'longitude' => 'decimal:7',
        'accuracy_meters' => 'decimal:2',
        'recorded_at' => 'datetime',
    ];

    public function session()
    {
        return $this->belongsTo(CourierDutySession::class, 'courier_duty_session_id');
    }

    public function courier()
    {
        return $this->belongsTo(Courier::class);
    }
}
