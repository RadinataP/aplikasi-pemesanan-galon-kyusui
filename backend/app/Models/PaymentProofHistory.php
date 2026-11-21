<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class PaymentProofHistory extends Model
{
    protected $fillable = [
        'payment_id', 'proof_image', 'proof_size', 'proof_mime_type',
        'proof_checksum', 'decision', 'note', 'actor_user_id', 'decided_at',
    ];

    protected $casts = [
        'proof_size' => 'integer',
        'decided_at' => 'datetime',
    ];

    public function payment()
    {
        return $this->belongsTo(Payment::class);
    }

    public function actor()
    {
        return $this->belongsTo(User::class, 'actor_user_id');
    }
}
