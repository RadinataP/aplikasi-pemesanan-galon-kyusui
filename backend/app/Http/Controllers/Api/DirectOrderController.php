<?php

namespace App\Http\Controllers\Api;

use App\Enums\AssignmentStatus;
use App\Enums\OrderStatus;
use App\Enums\PaymentMethod;
use App\Enums\PaymentStatus;
use App\Http\Controllers\Controller;
use App\Models\CourierAssignment;
use App\Models\Order;
use App\Models\Product;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Penjualan langsung (telepon, WhatsApp, atau pelanggan datang ke depot).
 * Owner dan Courier boleh mencatatnya. Untuk patch awal, transaksi harus sudah
 * dibayar CASH; QRIS tetap melalui alur bukti + verifikasi owner yang ada.
 */
class DirectOrderController extends Controller
{
    public function store(Request $request): JsonResponse
    {
        $actor = $request->user();
        $role = $actor?->role?->name;
        abort_unless(in_array($role, ['OWNER', 'COURIER'], true), 403, 'Akses ditolak.');

        $data = $request->validate([
            'customer_name' => ['nullable', 'string', 'max:120'],
            'customer_phone' => ['nullable', 'string', 'max:40'],
            'fulfillment_type' => ['required', 'in:DELIVERY,PICKUP'],
            'items' => ['required', 'array', 'min:1'],
            'items.*.product_id' => ['required', 'integer', 'distinct', 'exists:products,id'],
            'items.*.quantity' => ['required', 'integer', 'min:1', 'max:100'],
            'delivery_location' => ['required_if:fulfillment_type,DELIVERY', 'array'],
            'delivery_location.latitude' => ['required_if:fulfillment_type,DELIVERY', 'numeric', 'between:-90,90'],
            'delivery_location.longitude' => ['required_if:fulfillment_type,DELIVERY', 'numeric', 'between:-180,180'],
            'delivery_location.address' => ['required_if:fulfillment_type,DELIVERY', 'string', 'max:500'],
            'payment_method' => ['required', 'in:CASH'],
            'payment_received' => ['required', 'accepted'],
            'description' => ['nullable', 'string', 'max:500'],
        ]);

        $order = DB::transaction(function () use ($data, $actor, $role): Order {
            $products = Product::query()
                ->whereIn('id', array_column($data['items'], 'product_id'))
                ->orderBy('id')->lockForUpdate()->get()->keyBy('id');

            $subtotal = '0.00';
            $items = [];
            foreach ($data['items'] as $line) {
                $product = $products->get((int) $line['product_id']);
                abort_unless($product && $product->availability, 422, 'Produk tidak tersedia.');
                $quantity = (int) $line['quantity'];
                $lineTotal = bcmul((string) $product->price, (string) $quantity, 2);
                $subtotal = bcadd($subtotal, $lineTotal, 2);
                $items[] = [
                    'product_id' => $product->id,
                    'product_name' => $product->name,
                    'quantity' => $quantity,
                    'unit_price' => $product->price,
                    'line_total' => $lineTotal,
                ];
            }

            $fulfillment = $data['fulfillment_type'];
            // Tidak ada biaya antar untuk pesanan langsung maupun pesanan aplikasi.
            $deliveryFee = 0;
            $total = $subtotal;
            $location = $data['delivery_location'] ?? [];

            // Courier-created delivery is assigned to the acting courier; owner-created
            // order stays in the normal owner processing/assignment workflow.
            $isCourierDelivery = $role === 'COURIER' && $fulfillment === 'DELIVERY';
            $courier = $isCourierDelivery ? $actor->courier : null;
            abort_unless(!$isCourierDelivery || $courier !== null, 403, 'Profil kurir tidak ditemukan.');

            $order = Order::create([
                'customer_id' => null,
                'source' => 'DIRECT',
                'created_by_user_id' => $actor->id,
                'customer_name' => $data['customer_name'] ?? 'Pelanggan langsung',
                'customer_phone' => $data['customer_phone'] ?? null,
                'fulfillment_type' => $fulfillment,
                'order_status' => $isCourierDelivery ? OrderStatus::DITUGASKAN->value : OrderStatus::MENUNGGU_DIPROSES->value,
                'subtotal_amount' => $subtotal,
                'delivery_fee' => $deliveryFee,
                'total_amount' => $total,
                'delivery_address' => $fulfillment === 'DELIVERY' ? $location['address'] : 'Ambil di depot',
                'delivery_latitude' => $fulfillment === 'DELIVERY' ? (float) $location['latitude'] : null,
                'delivery_longitude' => $fulfillment === 'DELIVERY' ? (float) $location['longitude'] : null,
                'placed_at' => now(),
                'processed_at' => $role === 'OWNER' ? null : now(),
            ]);
            $order->items()->createMany($items);
            $order->statusHistories()->create([
                'from_status' => null,
                'to_status' => $order->order_status,
                'changed_by_user_id' => $actor->id,
                'note' => 'Pesanan langsung: '.($data['description'] ?? 'dicatat di luar aplikasi.'),
                'changed_at' => now(),
            ]);

            $payment = $order->payment()->create([
                'payment_method' => PaymentMethod::CASH->value,
                'payment_status' => PaymentStatus::PENDING->value,
                'amount' => $total,
            ]);
            // Pemasukan hanya dibuat karena request menyatakan uang sudah diterima.
            // transitionTo menjaga history pembayaran dan idempotensi buku kas.
            $payment->transitionTo(PaymentStatus::PAID, $actor->id);

            if ($isCourierDelivery) {
                CourierAssignment::create([
                    'order_id' => $order->id,
                    'courier_id' => $courier->id,
                    'status' => AssignmentStatus::ASSIGNED,
                    'assigned_at' => now(),
                ]);
            }

            return $order->load(['items', 'payment', 'assignments.courier.user']);
        });

        return response()->json([
            'data' => [
                'id' => $order->id,
                'order_number' => $order->order_number,
                'source' => $order->source,
                'created_by_user_id' => $order->created_by_user_id,
                'customer_name' => $order->customer_name,
                'customer_phone' => $order->customer_phone,
                'fulfillment_type' => $order->fulfillment_type,
                'items' => $order->items,
                'subtotal_amount' => $order->subtotal_amount,
                'delivery_fee' => $order->delivery_fee,
                'total_amount' => $order->total_amount,
                'order_status' => $order->order_status,
                'payment' => ['payment_method' => 'CASH', 'payment_status' => 'PAID', 'amount' => $order->total_amount],
            ],
            'message' => 'Pesanan langsung dan pemasukan berhasil dicatat.',
        ], 201);
    }
}
