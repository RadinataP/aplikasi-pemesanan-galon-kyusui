<?php

namespace App\Http\Controllers\Api;

use App\Enums\AssignmentStatus;
use App\Enums\OrderStatus;
use App\Enums\PaymentMethod;
use App\Enums\PaymentStatus;
use App\Events\BusinessActionOccurred;
use App\Http\Controllers\Controller;
use App\Http\Resources\CourierLocationResource;
use App\Http\Resources\PaymentResource;
use App\Models\CourierAssignment;
use App\Models\CourierLocation;
use App\Models\Notification;
use App\Models\Order;
use App\Models\Payment;
use App\Models\User;
use Carbon\CarbonImmutable;
use Exception;
use Illuminate\Database\QueryException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

class CourierController extends Controller
{
    /**
     * Preserved endpoint `POST /courier/orders/{order}/payment-confirmation`.
     *
     * Spec 06 section 12.4 mendefinisikan konfirmasi CASH sebagai aksi status
     * server-controlled. Endpoint ini tetap dipertahankan karena klien aktif
     * memakainya, tetapi sekarang konsisten dengan
     * `POST /courier/assignments/{assignment}/cash/confirm`: konflik state
     * menghasilkan `409`, bukan `404` (audit P2-16). `404` hanya dipakai saat
     * order memang tidak punya assignment milik kurir ini, yaitu saat
     * keberadaan resource tidak boleh bocor (spec 06 section 5.6).
     */
    public function confirmOrderPayment(Request $request, Order $order): JsonResponse
    {
        $courierId = $this->courierId($request);
        $assignment = $order->assignments()
            ->where('courier_id', $courierId)
            ->latest('id')
            ->first();

        abort_if($assignment === null, 404, 'Data tidak ditemukan.');
        abort_if(
            $assignment->status !== AssignmentStatus::ACTIVE,
            409,
            'Pembayaran tunai hanya dapat dikonfirmasi saat pengantaran aktif.',
        );

        return $this->cash($request, $assignment);
    }

    private function courierId(Request $request): int
    {
        $courierId = $request->user()?->courier?->id;

        abort_unless($courierId !== null, 403, 'Anda tidak memiliki akses sebagai kurir.');

        return $courierId;
    }

    /**
     * Spec 06 section 5.6: assignment milik courier lain harus 404, bukan 403,
     * supaya keberadaan pengantaran tidak bisa ditebak.
     */
    private function own(Request $request, CourierAssignment $assignment): CourierAssignment
    {
        abort_unless($assignment->courier_id === $this->courierId($request), 404);

        return $assignment;
    }

    /**
     * Bentuk payload assignment kurir.
     *
     * Preserved endpoint: nama key `status`, `subtotal`, `total`,
     * `payment.method`, dan `payment.status` sengaja dipertahankan karena
     * endpoint ini berada di luar katalog spec 06 section 30 dan klien aktif
     * memparsing key tersebut (`CourierAssignmentOrderDto`). Yang diubah hanya
     * sumber kolomnya menjadi nama canonical `order_status`, `subtotal_amount`,
     * `total_amount`, `payment_method`, dan `payment_status`
     * (`docs/05_KYUSUI_DATABASE_SCHEMA_REBUILT.md` section 11 dan
     * `docs/13_KYUSUI_DATABASE_FINALIZATION.md` section 9.2).
     */
    private function assignmentData(CourierAssignment $assignment): array
    {
        $assignment->load([
            'order.customer.user:id,name',
            'order.items.product',
            'order.payment',
            'latestLocation',
        ]);

        return [
            'id' => $assignment->id,
            'status' => $assignment->status,
            'assigned_at' => $assignment->assigned_at,
            'started_at' => $assignment->started_at,
            'completed_at' => $assignment->completed_at,
            'order' => [
                'id' => $assignment->order->id,
                'status' => $assignment->order->order_status,
                'subtotal' => $assignment->order->subtotal_amount,
                'total' => $assignment->order->total_amount,
                // Koordinat tujuan berasal dari pesanan yang dibuat pelanggan.
                // Pertahankan delivery_address sebagai key lama agar kompatibel
                // dengan klien Android versi sebelumnya.
                'delivery_address' => $assignment->order->delivery_address,
                'delivery_location' => $assignment->order->fulfillment_type === 'PICKUP'
                    ? null
                    : [
                        'address' => $assignment->order->delivery_address,
                        'latitude' => $assignment->order->delivery_latitude === null
                            ? null
                            : (float) $assignment->order->delivery_latitude,
                        'longitude' => $assignment->order->delivery_longitude === null
                            ? null
                            : (float) $assignment->order->delivery_longitude,
                    ],
                'customer' => [
                    'name' => $assignment->order->customer?->user?->name ?? $assignment->order->customer_name,
                ],
                'items' => $assignment->order->items->map(fn ($item) => [
                    'product_name' => $item->product_name ?? $item->product?->name,
                    'quantity' => $item->quantity,
                    'unit_price' => $item->unit_price,
                    'line_total' => $item->line_total,
                ]),
                'payment' => $assignment->order->payment === null ? null : [
                    'method' => $assignment->order->payment->payment_method,
                    'status' => $assignment->order->payment->payment_status,
                ],
            ],
            'latest_location' => $assignment->latestLocation,
        ];
    }

    /**
     * GET /courier/open-orders
     *
     * Daftar pesanan yang dapat diklaim oleh semua akun kurir ACTIVE.
     * Endpoint ini tidak mensyaratkan sesi duty aktif.
     *
     * Pesanan QRIS yang belum diverifikasi dapat diklaim; pembayaran wajib lunas sebelum pengantaran selesai.
     * Pesanan CASH dapat diklaim setelah metode pembayaran tersimpan.
     */
    public function openOrders(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        abort_unless(
            $request->user()->status === 'ACTIVE',
            403,
            'Akun kurir tidak aktif.'
        );

        $orders = Order::query()
            ->with(['customer.user:id,name', 'items.product', 'payment'])
            ->whereIn('order_status', [
                OrderStatus::MENUNGGU_PEMBAYARAN->value,
                OrderStatus::MENUNGGU_DIPROSES->value,
                OrderStatus::DIPROSES->value,
            ])
            ->whereHas('payment', function ($query): void {
                $query->whereIn('payment_method', [
                    PaymentMethod::CASH->value,
                    PaymentMethod::QRIS->value,
                ]);
            })
            ->where('fulfillment_type', 'DELIVERY')
            ->whereDoesntHave('assignments')
            ->latestPlaced()
            ->get();

        return response()->json([
            'data' => $orders->map(fn (Order $order) => [
                'order_id' => $order->id,
                'order_number' => $order->order_number,
                'order_status' => $order->order_status,
                'fulfillment_type' => $order->fulfillment_type,
                'customer' => [
                    'name' => $order->customer?->user?->name ?? $order->customer_name,
                ],
                'items' => $order->items->map(fn ($item) => [
                    'product_name' => $item->product_name,
                    'quantity' => $item->quantity,
                ]),
                // Format konsisten dengan OrderResource dan payload assignment.
                'delivery_address' => $order->delivery_address,
                'delivery_location' => $order->fulfillment_type === 'PICKUP'
                    ? null
                    : [
                        'address' => $order->delivery_address,
                        'latitude' => $order->delivery_latitude === null
                            ? null
                            : (float) $order->delivery_latitude,
                        'longitude' => $order->delivery_longitude === null
                            ? null
                            : (float) $order->delivery_longitude,
                    ],
                'subtotal_amount' => $order->subtotal_amount,
                'delivery_fee' => $order->delivery_fee,
                'total_amount' => $order->total_amount,
                'payment' => $order->payment === null ? null : [
                    'method' => $order->payment->payment_method,
                    'status' => $order->payment->payment_status,
                    // QRIS pending boleh diklaim dan mulai diantar.
                    'can_start_delivery' => true,
                ],
                'can_claim' => true,
            ]),
            'message' => 'Daftar pesanan terbuka berhasil dimuat.',
        ], 200);
    }

    /**
     * POST /courier/open-orders/{order}/claim
     *
     * Klaim atomik: row order dikunci sebelum pemeriksaan assignment dan
     * pembuatan assignment. Dua kurir yang mengklaim bersamaan tidak dapat
     * membuat dua assignment untuk order yang sama.
     */
    public function claimOrder(Request $request, Order $order): JsonResponse
    {
        $courierId = $this->courierId($request);

        // Akun harus aktif dan memiliki role COURIER; sesi duty tidak diwajibkan.
        abort_unless(
            $request->user()->status === 'ACTIVE'
                && $request->user()->role?->name === 'COURIER',
            403,
            'Akun kurir tidak aktif.'
        );

        try {
            $assignment = DB::transaction(function () use ($order, $courierId, $request): CourierAssignment {
                $lockedOrder = Order::query()
                    ->whereKey($order->id)
                    ->lockForUpdate()
                    ->firstOrFail();

                abort_unless(
                    in_array($lockedOrder->order_status, [
                        OrderStatus::MENUNGGU_PEMBAYARAN->value,
                        OrderStatus::MENUNGGU_DIPROSES->value,
                        OrderStatus::DIPROSES->value,
                    ], true),
                    409,
                    'Pesanan tidak lagi tersedia untuk diklaim.'
                );

                abort_unless($lockedOrder->fulfillment_type === 'DELIVERY', 409, 'Pesanan bukan pengantaran.');
                // Periksa ulang assignment setelah lock order didapat.
                abort_if(
                    CourierAssignment::query()->where('order_id', $lockedOrder->id)->exists(),
                    409,
                    'Pesanan sudah diambil oleh kurir lain.'
                );

                $payment = Payment::query()
                    ->where('order_id', $lockedOrder->id)
                    ->lockForUpdate()
                    ->first();

                abort_if($payment === null, 409, 'Metode pembayaran pesanan belum dipilih.');
                abort_unless(
                    in_array($payment->payment_method, [
                        PaymentMethod::CASH->value,
                        PaymentMethod::QRIS->value,
                    ], true),
                    409,
                    'Metode pembayaran pesanan tidak valid.'
                );

                $assignment = CourierAssignment::create([
                    'order_id' => $lockedOrder->id,
                    'courier_id' => $courierId,
                    'status' => AssignmentStatus::ACTIVE->value,
                    'assigned_at' => now(),
                    'started_at' => now(),
                ]);

                $previousStatus = $lockedOrder->order_status;
                $lockedOrder->update(['order_status' => OrderStatus::DALAM_PENGANTARAN->value]);
                $lockedOrder->statusHistories()->create([
                    'from_status' => $previousStatus,
                    'to_status' => OrderStatus::DALAM_PENGANTARAN->value,
                    'changed_by_user_id' => $request->user()->id,
                    'changed_at' => now(),
                    'note' => 'Pesanan diklaim oleh kurir melalui Open Delivery Pool.',
                ]);

                return $assignment;
            });
        } catch (HttpExceptionInterface $exception) {
            throw $exception;
        } catch (QueryException $exception) {
            return $this->serverError($exception, 'claim', new CourierAssignment([
                'order_id' => $order->id,
                'courier_id' => $courierId,
            ]));
        } catch (Exception $exception) {
            return $this->serverError($exception, 'claim', new CourierAssignment([
                'order_id' => $order->id,
                'courier_id' => $courierId,
            ]));
        }

        $claimedOrder = Order::query()->with('customer.user')->findOrFail($order->id);

        // Beri tahu pelanggan bahwa kurir sudah mengambil pesanan.
        if ($claimedOrder->customer !== null) event(new BusinessActionOccurred(
            userId: $claimedOrder->customer->user_id,
            type: 'COURIER_ASSIGNED',
            title: 'Kurir Mengambil Pesanan',
            body: 'Pesanan Anda telah diambil oleh kurir.',
            data: [
                'order_id' => $claimedOrder->id,
                'assignment_id' => $assignment->id,
            ],
        ));

        // Beri tahu Owner untuk monitoring, bukan meminta persetujuan.
        User::query()
            ->whereHas('role', fn ($query) => $query->where('name', 'OWNER'))
            ->pluck('id')
            ->each(fn (int $ownerUserId) => event(new BusinessActionOccurred(
                userId: $ownerUserId,
                type: 'COURIER_ASSIGNED',
                title: 'Pesanan Diambil Kurir',
                body: 'Seorang kurir telah mengambil tugas pengantaran.',
                data: [
                    'order_id' => $claimedOrder->id,
                    'assignment_id' => $assignment->id,
                ],
            )));

        return response()->json([
            'data' => $this->assignmentData($assignment),
            'message' => 'Pesanan diterima. Pengantaran dan tracking aktif.',
        ], 201);
    }

    public function activeAssignmentIds(Request $request): JsonResponse
    {
        return response()->json(['data' => CourierAssignment::query()
            ->where('courier_id', $this->courierId($request))
            ->where('status', AssignmentStatus::ACTIVE->value)
            ->orderBy('id')->pluck('id')->map(fn ($id) => (string) $id)->values()]);
    }

    public function index(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        $assignments = CourierAssignment::where('courier_id', $courierId)
            ->with(['order.customer.user:id,name', 'order.items.product', 'order.payment'])
            ->latest('courier_assignments.created_at')
            ->get();

        return response()->json([
            'data' => $assignments->map(fn ($a) => $this->assignmentData($a)),
            'message' => 'Data pengantaran berhasil diambil.',
        ], 200);
    }

    public function show(Request $request, CourierAssignment $assignment): JsonResponse
    {
        return response()->json([
            'data' => $this->assignmentData($this->own($request, $assignment)),
            'message' => 'Data pengantaran berhasil diambil.',
        ], 200);
    }

    public function start(Request $request, CourierAssignment $assignment): JsonResponse
    {
        $this->own($request, $assignment);

        try {
            $assignment = DB::transaction(function () use ($assignment, $request): CourierAssignment {
                $lockedAssignment = CourierAssignment::query()
                    ->whereKey($assignment->id)
                    ->lockForUpdate()
                    ->firstOrFail();
                $order = Order::query()->whereKey($lockedAssignment->order_id)->lockForUpdate()->firstOrFail();

                abort_unless(
                    $lockedAssignment->status === AssignmentStatus::ASSIGNED
                    && $order->order_status === OrderStatus::DITUGASKAN->value,
                    409,
                    'Pengantaran tidak dapat dimulai karena status pengantaran tidak sesuai.'
                );

                abort_unless($order->payment()->exists(), 409, 'Pembayaran pesanan tidak ditemukan.');

                $lockedAssignment->update([
                    'status' => AssignmentStatus::ACTIVE->value,
                    'started_at' => now(),
                ]);
                $order->update(['order_status' => OrderStatus::DALAM_PENGANTARAN->value]);
                $order->statusHistories()->create([
                    'from_status' => OrderStatus::DITUGASKAN->value,
                    'to_status' => OrderStatus::DALAM_PENGANTARAN->value,
                    'changed_by_user_id' => $request->user()->id,
                    'changed_at' => now(),
                ]);

                Log::info('Courier delivery started.', $this->logContext($lockedAssignment, $request));

                return $lockedAssignment->fresh();
            });
        } catch (HttpExceptionInterface $exception) {
            throw $exception;
        } catch (QueryException $exception) {
            return $this->serverError($exception, 'start', $assignment);
        } catch (Exception $exception) {
            return $this->serverError($exception, 'start', $assignment);
        }

        $order = Order::query()->with('customer.user')->findOrFail($assignment->order_id);
        if ($order->customer !== null) event(new BusinessActionOccurred(
            userId: $order->customer->user_id,
            type: 'DELIVERY_STARTED',
            title: 'Pengantaran Dimulai',
            body: 'Pesanan Anda sedang diantarkan oleh Courier.',
            // `order_status` adalah event-specific field untuk
            // `DELIVERY_STARTED` pada spec 10 §16 dan tabel §19.
            data: [
                'order_id' => $order->id,
                'assignment_id' => $assignment->id,
                'order_status' => $order->order_status,
            ],
        ));

        return response()->json([
            'data' => $this->assignmentData($assignment),
            'message' => 'Pengantaran berhasil dimulai.',
        ], 200);
    }

    public function location(Request $request, CourierAssignment $assignment): JsonResponse
    {
        $this->own($request, $assignment);

        $validated = $request->validate([
            'latitude' => ['required', 'numeric', 'between:-90,90'],
            'longitude' => ['required', 'numeric', 'between:-180,180'],
            'accuracy_meters' => ['required', 'numeric', 'min:0', 'max:10000'],
            'recorded_at' => ['nullable', 'date'],
        ]);

        try {
            $location = DB::transaction(function () use ($assignment, $request, $validated): CourierLocation {
                \App\Models\Courier::query()->whereKey($assignment->courier_id)->lockForUpdate()->firstOrFail();
                $lockedAssignment = CourierAssignment::query()
                    ->whereKey($assignment->id)
                    ->lockForUpdate()
                    ->firstOrFail();
                $order = Order::query()->whereKey($lockedAssignment->order_id)->lockForUpdate()->firstOrFail();

                abort_unless(
                    $lockedAssignment->status === AssignmentStatus::ACTIVE
                    && $order->order_status === OrderStatus::DALAM_PENGANTARAN->value,
                    409,
                    'Lokasi belum dapat diperbarui karena pengantaran belum aktif.'
                );

                $recordedAt = isset($validated['recorded_at'])
                    ? CarbonImmutable::parse($validated['recorded_at'])
                    : CarbonImmutable::now();
                $previous = CourierLocation::query()
                    ->where('courier_assignment_id', $lockedAssignment->id)
                    ->latest('recorded_at')
                    ->latest('id')
                    ->first();

                if ($previous !== null) {
                    $distanceKm = $this->haversineDistanceKm(
                        (float) $previous->latitude,
                        (float) $previous->longitude,
                        (float) $validated['latitude'],
                        (float) $validated['longitude'],
                    );
                    $elapsedSeconds = $recordedAt->getTimestamp() - $previous->recorded_at->getTimestamp();

                    abort_if(
                        $distanceKm > 0 && $elapsedSeconds <= 0,
                        422,
                        'Waktu lokasi harus lebih baru dari lokasi sebelumnya.'
                    );
                    $speedKmh = $elapsedSeconds > 0 ? $distanceKm / ($elapsedSeconds / 3600) : 0;
                    abort_if($speedKmh > 150, 422, 'Perubahan lokasi tidak valid karena kecepatannya terlalu tinggi.');
                }

                $location = $lockedAssignment->locations()->create([
                    'courier_id' => $lockedAssignment->courier_id,
                    'latitude' => $validated['latitude'],
                    'longitude' => $validated['longitude'],
                    'accuracy_meters' => $validated['accuracy_meters'],
                    'recorded_at' => $recordedAt,
                ]);

                // Satu GPS fix untuk seluruh pesanan aktif milik kurir ini.
                $others = CourierAssignment::query()
                    ->where('courier_id', $lockedAssignment->courier_id)
                    ->where('status', AssignmentStatus::ACTIVE->value)
                    ->where('id', '!=', $lockedAssignment->id)
                    ->lockForUpdate()->get();
                foreach ($others as $other) {
                    $other->locations()->create([
                        'courier_id' => $other->courier_id,
                        'latitude' => $validated['latitude'],
                        'longitude' => $validated['longitude'],
                        'accuracy_meters' => $validated['accuracy_meters'],
                        'recorded_at' => $recordedAt,
                    ]);
                }

                Log::info('Courier location updated.', $this->logContext($lockedAssignment, $request));

                return $location;
            });
        } catch (HttpExceptionInterface $exception) {
            throw $exception;
        } catch (QueryException $exception) {
            return $this->serverError($exception, 'location', $assignment);
        } catch (Exception $exception) {
            return $this->serverError($exception, 'location', $assignment);
        }

        $active = CourierAssignment::query()->where('courier_id', $assignment->courier_id)
            ->where('status', AssignmentStatus::ACTIVE->value)->with('order.customer')->get();
        foreach ($active as $delivery) {
            $customerUserId = $delivery->order?->customer?->user_id;
            if ($customerUserId === null) continue;
            if (!Notification::query()->where('user_id', $customerUserId)
                ->where('type', 'TRACKING_AVAILABLE')->whereJsonContains('data->assignment_id', $delivery->id)->exists()) {
                event(new BusinessActionOccurred(userId: $customerUserId, type: 'TRACKING_AVAILABLE',
                    title: 'Tracking Tersedia', body: 'Lokasi kurir untuk pesanan Anda tersedia.',
                    data: ['order_id' => $delivery->order_id, 'assignment_id' => $delivery->id]));
            }
        }

        // Spec 09 §29.1 menetapkan `data.location` berisi koordinat, accuracy, dan
        // waktu rekaman saja. Sebelum ini endpoint mengembalikan model
        // Eloquent mentah sehingga `courier_assignment_id` dan `courier_id`
        // ikut terekspos ke klien.
        return response()->json([
            'data' => [
                'accepted' => true,
                'location' => new CourierLocationResource($location),
            ],
            'message' => 'Lokasi berhasil diperbarui.',
        ], 200);
    }

    public function cash(Request $request, CourierAssignment $assignment): JsonResponse
    {
        $this->own($request, $assignment);

        try {
            $payment = DB::transaction(function () use ($assignment, $request): Payment {
                $lockedAssignment = CourierAssignment::query()
                    ->whereKey($assignment->id)
                    ->lockForUpdate()
                    ->firstOrFail();
                $order = Order::query()->whereKey($lockedAssignment->order_id)->lockForUpdate()->firstOrFail();

                abort_unless(
                    $lockedAssignment->status === AssignmentStatus::ACTIVE
                    && $order->order_status === OrderStatus::DALAM_PENGANTARAN->value,
                    409,
                    'Pembayaran tunai hanya dapat dikonfirmasi saat pengantaran aktif.'
                );

                $payment = Payment::query()->where('order_id', $order->id)->lockForUpdate()->first();
                abort_if($payment === null, 409, 'Pembayaran untuk pesanan ini tidak ditemukan.');
                abort_if($payment->payment_method !== PaymentMethod::CASH->value, 409, 'Pembayaran ini bukan pembayaran tunai.');
                abort_if($payment->payment_status === PaymentStatus::PAID->value, 409, 'Pembayaran sudah lunas.');
                abort_unless($payment->payment_status === PaymentStatus::PENDING->value, 409, 'Pembayaran tidak dapat dikonfirmasi pada status saat ini.');

                $payment->transitionTo(PaymentStatus::PAID, $request->user()->id);
                Log::info('Courier cash payment confirmed.', $this->logContext($lockedAssignment, $request));

                return $payment->fresh();
            });
        } catch (HttpExceptionInterface $exception) {
            throw $exception;
        } catch (QueryException $exception) {
            return $this->serverError($exception, 'cash', $assignment);
        } catch (Exception $exception) {
            return $this->serverError($exception, 'cash', $assignment);
        }

        $order = Order::query()
            ->with('customer.user')
            ->findOrFail($assignment->order_id);
        if ($order->customer !== null) event(new BusinessActionOccurred(
            userId: $order->customer->user_id,
            type: 'PAYMENT_CASH_CONFIRMED',
            title: 'Pembayaran Tunai Dikonfirmasi',
            body: 'Pembayaran tunai pesanan Anda telah dikonfirmasi.',
            // `payment_method` dan `payment_status` adalah event-specific field untuk
            // `PAYMENT_CASH_CONFIRMED` pada spec 10 §11 dan tabel §19.
            data: [
                'order_id' => $order->id,
                'payment_id' => $payment->id,
                'payment_method' => $payment->payment_method,
                'payment_status' => $payment->payment_status,
            ],
        ));

        return response()->json([
            // Spec 06 section 14.2 menetapkan konfirmasi CASH di dalam
            // `data.payment`, bukan datar datar. Bentuk payment memakai
            // PaymentResource (spec 06 section 7.5) supaya hanya ada satu
            // representasi payment di API dan tidak ada field yang diarang.
            'data' => [
                'payment' => new PaymentResource($payment),
            ],
            'message' => 'Pembayaran tunai berhasil dikonfirmasi.',
        ], 200);
    }

    public function complete(Request $request, CourierAssignment $assignment): JsonResponse
    {
        $this->own($request, $assignment);

        try {
            $assignment = DB::transaction(function () use ($assignment, $request): CourierAssignment {
                $lockedAssignment = CourierAssignment::query()
                    ->whereKey($assignment->id)
                    ->lockForUpdate()
                    ->firstOrFail();
                $order = Order::query()->whereKey($lockedAssignment->order_id)->lockForUpdate()->firstOrFail();
                $payment = Payment::query()->where('order_id', $order->id)->lockForUpdate()->first();

                abort_unless(
                    $lockedAssignment->status === AssignmentStatus::ACTIVE
                    && $order->order_status === OrderStatus::DALAM_PENGANTARAN->value,
                    409,
                    'Pengantaran tidak dapat diselesaikan karena status saat ini tidak sesuai.'
                );
                abort_if($payment === null, 409, 'Pengantaran tidak dapat diselesaikan karena pembayaran tidak ditemukan.');
                abort_unless(
                    in_array($payment->payment_method, [PaymentMethod::QRIS->value, PaymentMethod::CASH->value], true)
                    && $payment->payment_status === PaymentStatus::PAID->value,
                    409,
                    'Pengantaran tidak dapat diselesaikan karena pembayaran belum lunas.'
                );

                $lockedAssignment->update([
                    'status' => AssignmentStatus::COMPLETED->value,
                    'completed_at' => now(),
                ]);
                $order->update([
                    'order_status' => OrderStatus::SELESAI->value,
                    'completed_at' => now(),
                ]);
                $order->statusHistories()->create([
                    'from_status' => OrderStatus::DALAM_PENGANTARAN->value,
                    'to_status' => OrderStatus::SELESAI->value,
                    'changed_by_user_id' => $request->user()->id,
                    'changed_at' => now(),
                ]);

                Log::info('Courier delivery completed.', $this->logContext($lockedAssignment, $request));

                return $lockedAssignment->fresh();
            });
        } catch (HttpExceptionInterface $exception) {
            throw $exception;
        } catch (QueryException $exception) {
            return $this->serverError($exception, 'complete', $assignment);
        } catch (Exception $exception) {
            return $this->serverError($exception, 'complete', $assignment);
        }

        $completedOrder = Order::query()->with(['customer.user', 'assignments.courier.user'])->findOrFail($assignment->order_id);
        // `order_status` adalah event-specific field untuk
        // `ORDER_COMPLETED` pada spec 10 §18 dan tabel §19.
        $notificationData = [
            'order_id' => $completedOrder->id,
            'assignment_id' => $assignment->id,
            'order_status' => $completedOrder->order_status,
        ];
        $recipientUserIds = collect([$completedOrder->customer?->user_id, $request->user()->id])
            ->merge(
                User::query()
                    ->whereHas('role', fn ($query) => $query->where('name', 'OWNER'))
                    ->pluck('id')
            )
            ->filter()->unique();
        $recipientUserIds->each(fn (int $recipientUserId) => event(new BusinessActionOccurred(
            userId: $recipientUserId,
            type: 'ORDER_COMPLETED',
            title: 'Pesanan Selesai',
            body: 'Pesanan telah selesai diantarkan.',
            data: $notificationData,
        )));

        return response()->json([
            'data' => $this->assignmentData($assignment),
            'message' => 'Pengantaran berhasil diselesaikan.',
        ], 200);
    }

    private function haversineDistanceKm(float $latitudeFrom, float $longitudeFrom, float $latitudeTo, float $longitudeTo): float
    {
        $earthRadiusKm = 6371.0;
        $latitudeDelta = deg2rad($latitudeTo - $latitudeFrom);
        $longitudeDelta = deg2rad($longitudeTo - $longitudeFrom);
        $a = sin($latitudeDelta / 2) ** 2
            + cos(deg2rad($latitudeFrom)) * cos(deg2rad($latitudeTo)) * sin($longitudeDelta / 2) ** 2;

        return $earthRadiusKm * 2 * asin(min(1, sqrt($a)));
    }

    private function logContext(CourierAssignment $assignment, Request $request): array
    {
        return [
            'assignment_id' => $assignment->id,
            'courier_id' => $assignment->courier_id,
            'order_id' => $assignment->order_id,
            'user_id' => $request->user()->id,
        ];
    }

    private function serverError(Exception $exception, string $action, CourierAssignment $assignment): JsonResponse
    {
        Log::error('Courier action failed.', [
            'action' => $action,
            'assignment_id' => $assignment->id,
            'courier_id' => $assignment->courier_id,
            'order_id' => $assignment->order_id,
            'exception' => $exception,
        ]);

        return response()->json([
            'data' => null,
            'message' => 'Tindakan kurir tidak dapat diproses saat ini.',
        ], 500);
    }
}
