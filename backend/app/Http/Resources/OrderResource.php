<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * Order Resource.
 *
 * Bentuk mengikuti `docs/06_KYUSUI_API_SPECIFICATION_REBUILT.md` section 7.4.
 * Nama kolom database sudah sama dengan nama field canonical `order_number`,
 * `subtotal_amount`, `total_amount`, dan `order_status`
 * (`docs/05_KYUSUI_DATABASE_SCHEMA_REBUILT.md` section 11).
 *
 * Payment dan assignment bersifat nested. Nilai payment boleh `null` bila
 * payment record belum dibuat; customer memilih metode melalui
 * `POST /customer/orders/{order}/payment` (06 section 11.2 dan section 30).
 */
class OrderResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $payment = $this->relationLoaded('payment') ? $this->payment : null;

        return [
            'id' => $this->id,
            'order_number' => $this->order_number,
            'customer_id' => $this->customer_id,
            // Metadata sumber transaksi untuk membedakan order aplikasi dan order langsung.
            'source' => $this->source ?? 'APP',
            'created_by_user_id' => $this->created_by_user_id,
            'customer_name' => $this->customer_name,
            'customer_phone' => $this->customer_phone,
            'fulfillment_type' => $this->fulfillment_type ?? 'DELIVERY',
            'items' => OrderItemResource::collection($this->whenLoaded('items')),
            'subtotal_amount' => $this->subtotal_amount,
            'delivery_fee' => $this->delivery_fee,
            'total_amount' => $this->total_amount,
            'order_status' => $this->order_status,

            // Koordinat dan alamat tujuan harus dikirim kembali ke Android.
            // Tracking memakai latitude/longitude ini untuk marker tujuan dan
            // routing OSRM; tanpa field ini Android hanya menerima null.
            // Field tetap disertakan untuk pickup, dengan nilai null jika
            // pesanan memang tidak memiliki alamat pengantaran.
            'delivery_location' => $this->fulfillment_type === 'PICKUP'
                ? null
                : [
                    'address' => $this->delivery_address,
                    'latitude' => $this->delivery_latitude === null
                        ? null
                        : (float) $this->delivery_latitude,
                    'longitude' => $this->delivery_longitude === null
                        ? null
                        : (float) $this->delivery_longitude,
                ],
            'payment' => $payment === null ? null : [
                'id' => $payment->id,
                'payment_method' => $payment->payment_method,
                'payment_status' => $payment->payment_status,
                'amount' => $payment->amount,
            ],
            'assignment' => $this->whenLoaded('assignments', fn (): ?array => $this->latestAssignmentPayload()),
        ];
    }

    /**
     * Assignment yang ditampilkan selalu assignment terakhir, mengikuti urutan
     * operasional `assignments DESC LIMIT 1` yang dipakai dashboard owner.
     *
     * @return array<string, mixed>|null
     */
    private function latestAssignmentPayload(): ?array
    {
        $assignment = $this->assignments->sortByDesc('id')->first();

        if ($assignment === null) {
            return null;
        }

        return [
            'id' => $assignment->id,
            'status' => $assignment->status,
            'courier' => $assignment->relationLoaded('courier') && $assignment->courier !== null
                ? [
                    'id' => $assignment->courier->id,
                    'name' => $assignment->courier->user?->name,
                ]
                : null,
        ];
    }
}
