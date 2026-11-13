<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Resources\CourierLocationResource;
use App\Models\CourierDutyLocation;
use App\Models\CourierDutySession;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Sesi bertugas kurir (on-duty) dan pengiriman lokasi tanpa assignment.
 *
 * ## Kenapa controller terpisah
 *
 * `CourierController` assignment-centric: setiap aksi berangkat dari satu
 * assignment aktif. Sesi bertugas tidak punya assignment, jadi lifecycle-nya
 * dipisahkan agar kontrak assignment (dan tracking customer yang membacanya)
 * tidak berubah. Karena itu lokasi sesi bertugas disimpan di
 * `courier_duty_locations`, bukan di `courier_locations`.
 *
 * ## Otorisasi
 *
 * Seluruh endpoint berada di grup `prefix('courier')->middleware('role:COURIER')`.
 * Controller tetap memeriksa bahwa user benar-benar punya profil kurir, sama
 * seperti `CourierController`, supaya akun ber-role COURIER tanpa profil kurir
 * tidak pernah bisa memulai sesi.
 *
 * ## Pencegahan update dari sesi yang sudah berakhir
 *
 * `location` selalu mencari sesi AKTIF milik kurir di database, bukan memercayai
 * id dari klien. Klien boleh mengirim `duty_session_id` sebagai pengaman
 * tambahan; bila id itu tidak sama dengan sesi aktif, permintaan ditolak `409`.
 * Dengan begitu titik dari sesi yang sudah berakhir tidak pernah tersimpan.
 */
class CourierDutyController extends Controller
{
    /**
     * Batas kecepatan antar-titik, samakan dengan `CourierController::location`
     * supaya tidak ada dua aturan berbeda untuk data lokasi kurir.
     */
    private const MAX_SPEED_KMH = 150;

    private const SESSION_ENDS_NOTE = 'Sesi bertugas sudah berakhir. Mulai sesi baru untuk mengirim lokasi.';

    /**
     * `GET /courier/duty` — status sesi bertugas saat ini.
     */
    public function show(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        $session = CourierDutySession::query()
            ->where('courier_id', $courierId)
            ->active()
            ->with('latestLocation')
            ->first();

        $latestLocation = $session?->latestLocation;

        return response()->json([
            'data' => [
                'active' => $session !== null,
                'session' => $session === null ? null : $this->sessionData($session),
                'latest_location' => $latestLocation === null
                    ? null
                    : new CourierLocationResource($latestLocation),
            ],
            'message' => 'Status bertugas berhasil dimuat.',
        ], 200);
    }

    /**
     * `POST /courier/duty/start`.
     *
     * Satu kurir hanya boleh punya satu sesi aktif. Sesi kedua ditolak `409`.
     */
    public function start(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        $session = DB::transaction(function () use ($courierId): CourierDutySession {
            $alreadyActive = CourierDutySession::query()
                ->where('courier_id', $courierId)
                ->active()
                ->lockForUpdate()
                ->exists();

            abort_if($alreadyActive, 409, 'Kurir sudah dalam sesi bertugas.');

            return CourierDutySession::create([
                'courier_id' => $courierId,
                'started_at' => now(),
            ]);
        });

        return response()->json([
            'data' => $this->sessionData($session),
            'message' => 'Sesi bertugas dimulai.',
        ], 200);
    }

    /**
     * `POST /courier/duty/stop` — mengakhiri sesi aktif.
     */
    public function stop(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        $session = DB::transaction(function () use ($courierId): CourierDutySession {
            $active = CourierDutySession::query()
                ->where('courier_id', $courierId)
                ->active()
                ->lockForUpdate()
                ->first();

            abort_if($active === null, 409, 'Tidak ada sesi bertugas yang aktif.');

            $active->update(['ended_at' => now()]);

            return $active->fresh();
        });

        return response()->json([
            'data' => $this->sessionData($session),
            'message' => 'Sesi bertugas diakhiri.',
        ], 200);
    }

    /**
     * `POST /courier/duty/location` — menyimpan satu titik posisi kurir yang
     * sedang bertugas. Validasi koordinat dan kecepatan sama dengan
     * `CourierController::location`.
     */
    public function location(Request $request): JsonResponse
    {
        $courierId = $this->courierId($request);

        $validated = $request->validate([
            'latitude' => ['required', 'numeric', 'between:-90,90'],
            'longitude' => ['required', 'numeric', 'between:-180,180'],
            'accuracy_meters' => ['required', 'numeric', 'min:0', 'max:10000'],
            'recorded_at' => ['nullable', 'date'],
            'duty_session_id' => ['sometimes', 'nullable', 'integer'],
        ]);

        $location = DB::transaction(function () use ($courierId, $validated): CourierDutyLocation {
            $session = CourierDutySession::query()
                ->where('courier_id', $courierId)
                ->active()
                ->lockForUpdate()
                ->first();

            abort_if(
                $session === null,
                409,
                'Lokasi belum dapat diperbarui karena tidak ada sesi bertugas yang aktif.'
            );

            $claimedSessionId = $validated['duty_session_id'] ?? null;
            if ($claimedSessionId !== null) {
                abort_unless(
                    (int) $claimedSessionId === $session->id,
                    409,
                    self::SESSION_ENDS_NOTE
                );
            }

            $recordedAt = isset($validated['recorded_at'])
                ? CarbonImmutable::parse($validated['recorded_at'])
                : CarbonImmutable::now();

            $previous = CourierDutyLocation::query()
                ->where('courier_duty_session_id', $session->id)
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
                abort_if(
                    $speedKmh > self::MAX_SPEED_KMH,
                    422,
                    'Perubahan lokasi tidak valid karena kecepatannya terlalu tinggi.'
                );
            }

            return CourierDutyLocation::create([
                'courier_duty_session_id' => $session->id,
                'courier_id' => $courierId,
                'latitude' => $validated['latitude'],
                'longitude' => $validated['longitude'],
                'accuracy_meters' => $validated['accuracy_meters'],
                'recorded_at' => $recordedAt,
            ]);
        });

        return response()->json([
            'data' => [
                'accepted' => true,
                'location' => new CourierLocationResource($location),
            ],
            'message' => 'Lokasi berhasil diperbarui.',
        ], 200);
    }

    private function courierId(Request $request): int
    {
        $courierId = $request->user()->courier?->id;

        abort_unless($courierId !== null, 403, 'Anda tidak memiliki akses sebagai kurir.');

        return $courierId;
    }

    /**
     * @return array<string, mixed>
     */
    private function sessionData(CourierDutySession $session): array
    {
        return [
            'id' => $session->id,
            'started_at' => $session->started_at,
            'ended_at' => $session->ended_at,
            'is_active' => $session->isActive(),
        ];
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
}
