<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

/**
 * Satu baris buku kas untuk uang yang benar-benar masuk atau keluar.
 * Ini bukan tabel order: order yang belum dibayar tidak otomatis menjadi income.
 */
class FinancialTransaction extends Model
{
    protected $fillable = [
        'type', 'category', 'amount', 'payment_method', 'source', 'order_id',
        'payment_id', 'recorded_by_user_id', 'reversal_of_id', 'description', 'occurred_at',
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'occurred_at' => 'datetime',
    ];

    public function order() { return $this->belongsTo(Order::class); }
    public function payment() { return $this->belongsTo(Payment::class); }
    public function recorder() { return $this->belongsTo(User::class, 'recorded_by_user_id'); }
    public function reversalOf() { return $this->belongsTo(self::class, 'reversal_of_id'); }
}
