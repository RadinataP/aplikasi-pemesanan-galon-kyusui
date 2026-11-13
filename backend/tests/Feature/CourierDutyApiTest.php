<?php

namespace Tests\Feature;

use App\Models\Courier;
use App\Models\CourierDutyLocation;
use App\Models\CourierDutySession;
use App\Models\Role;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Route;
use Tests\TestCase;

/**
 * Sesi bertugas kurir (on-duty) dan pengiriman lokasi tanpa assignment.
 *
 * Siklus yang diuji: mulai tugas -> update lokasi -> berhenti tugas, plus
 * otorisasi, validasi koordinat, validasi kecepatan, dan penolakan update dari
 * sesi yang sudah berakhir.
 */
class CourierDutyApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        foreach (['CUSTOMER', 'OWNER', 'COURIER'] as $name) {
            Role::create(['name' => $name]);
        }
    }

    public function test_duty_endpoints_require_the_courier_role(): void
    {
        $customer = User::factory()->create(['role_id' => Role::where('name', 'CUSTOMER')->value('id')]);
        $owner = User::factory()->create(['role_id' => Role::where('name', 'OWNER')->value('id')]);

        foreach ([$customer, $owner] as $user) {
            $this->actingAs($user)->getJson('/api/v1/courier/duty')->assertForbidden();
            $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertForbidden();
            $this->actingAs($user)->postJson('/api/v1/courier/duty/stop')->assertForbidden();
            $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
                'latitude' => -6.2,
                'longitude' => 106.8,
                'accuracy_meters' => 5,
            ])->assertForbidden();
        }
    }

    public function test_a_courier_role_without_a_courier_profile_is_rejected(): void
    {
        $user = User::factory()->create(['role_id' => Role::where('name', 'COURIER')->value('id')]);

        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')
            ->assertForbidden()
            ->assertJson(['message' => 'Anda tidak memiliki akses sebagai kurir.']);
    }

    public function test_start_creates_a_single_active_session(): void
    {
        [$user, $courier] = $this->courier();

        $response = $this->actingAs($user)->postJson('/api/v1/courier/duty/start')
            ->assertOk()
            ->assertJsonPath('message', 'Sesi bertugas dimulai.')
            ->assertJsonPath('data.is_active', true);

        $this->assertDatabaseHas('courier_duty_sessions', [
            'courier_id' => $courier->id,
            'ended_at' => null,
        ]);
        $this->assertNotNull($response->json('data.id'));
    }

    public function test_starting_a_second_session_conflicts(): void
    {
        [$user] = $this->courier();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();
        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')
            ->assertStatus(409)
            ->assertJson(['message' => 'Kurir sudah dalam sesi bertugas.']);

        $this->assertDatabaseCount('courier_duty_sessions', 1);
    }

    public function test_stop_ends_the_active_session_and_is_not_repeatable(): void
    {
        [$user] = $this->courier();

        $started = $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/stop')
            ->assertOk()
            ->assertJsonPath('message', 'Sesi bertugas diakhiri.')
            ->assertJsonPath('data.is_active', false);

        $this->assertNotNull(
            CourierDutySession::query()->whereKey($started->json('data.id'))->first()->ended_at
        );

        $this->actingAs($user)->postJson('/api/v1/courier/duty/stop')
            ->assertStatus(409)
            ->assertJson(['message' => 'Tidak ada sesi bertugas yang aktif.']);
    }

    public function test_location_requires_an_active_session(): void
    {
        [$user] = $this->courier();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
        ])
            ->assertStatus(409)
            ->assertJson(['message' => 'Lokasi belum dapat diperbarui karena tidak ada sesi bertugas yang aktif.']);

        $this->assertDatabaseCount('courier_duty_locations', 0);
    }

    public function test_location_is_stored_for_the_active_session_and_not_in_assignment_locations(): void
    {
        [$user, $courier] = $this->courier();
        $started = $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();
        $sessionId = $started->json('data.id');

        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
            'duty_session_id' => $sessionId,
        ])
            ->assertOk()
            ->assertJsonPath('message', 'Lokasi berhasil diperbarui.')
            ->assertJsonPath('data.accepted', true)
            ->assertJsonStructure(['data' => ['location' => ['latitude', 'longitude', 'accuracy_meters', 'recorded_at']]]);

        $this->assertDatabaseHas('courier_duty_locations', [
            'courier_duty_session_id' => $sessionId,
            'courier_id' => $courier->id,
        ]);
        // Kontrak tracking customer (courier_locations) tidak boleh tersentuh.
        $this->assertDatabaseCount('courier_locations', 0);
    }

    public function test_location_from_an_ended_session_is_rejected(): void
    {
        [$user] = $this->courier();
        $started = $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();
        $endedSessionId = $started->json('data.id');

        $this->actingAs($user)->postJson('/api/v1/courier/duty/stop')->assertOk();

        // Sesi baru dimulai; klien yang masih memegang id sesi lama harus ditolak.
        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
            'duty_session_id' => $endedSessionId,
        ])
            ->assertStatus(409)
            ->assertJson(['message' => 'Sesi bertugas sudah berakhir. Mulai sesi baru untuk mengirim lokasi.']);

        $this->assertDatabaseCount('courier_duty_locations', 0);

        // Tanpa sesi aktif sama sekali, pengiriman ditolak sebagai "tidak ada sesi".
        $soloUser = $this->courier('solo@example.com')[0];
        $this->actingAs($soloUser)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
        ])->assertStatus(409);
    }

    public function test_location_validates_coordinates(): void
    {
        [$user] = $this->courier();
        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => 200,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
        ])->assertUnprocessable();

        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 500,
            'accuracy_meters' => 5,
        ])->assertUnprocessable();

        $this->assertDatabaseCount('courier_duty_locations', 0);
    }

    public function test_location_rejects_an_impossible_speed_between_two_points(): void
    {
        [$user] = $this->courier();
        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();

        // ~14 km dalam 10 detik kira-kira 5300 km/jam, jauh di atas 150 km/jam.
        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
            'recorded_at' => now()->subSeconds(10)->toIso8601String(),
        ])->assertOk();

        // Handler exception global menormalkan pesan 422 (sama seperti jalur
        // assignment), jadi yang diperiksa hanya statusnya.
        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.3,
            'longitude' => 106.9,
            'accuracy_meters' => 5,
            'recorded_at' => now()->toIso8601String(),
        ])->assertStatus(422);

        $this->assertDatabaseCount('courier_duty_locations', 1);
    }

    public function test_show_reports_active_state_and_latest_location(): void
    {
        [$user] = $this->courier();

        $this->actingAs($user)->getJson('/api/v1/courier/duty')
            ->assertOk()
            ->assertJsonPath('data.active', false)
            ->assertJsonPath('data.session', null)
            ->assertJsonPath('data.latest_location', null);

        $this->actingAs($user)->postJson('/api/v1/courier/duty/start')->assertOk();
        $this->actingAs($user)->postJson('/api/v1/courier/duty/location', [
            'latitude' => -6.2,
            'longitude' => 106.8,
            'accuracy_meters' => 5,
        ])->assertOk();

        $this->actingAs($user)->getJson('/api/v1/courier/duty')
            ->assertOk()
            ->assertJsonPath('data.active', true)
            ->assertJsonPath('data.session.is_active', true)
            ->assertJsonPath('data.latest_location.latitude', '-6.2000000');
    }

    public function test_duty_location_route_has_throttle_middleware(): void
    {
        $route = Route::getRoutes()->getByName('api.v1.courier.duty.location');
        $this->assertNotNull($route, 'Route duty location harus terdaftar');
        $this->assertContains('throttle:120,1', $route->gatherMiddleware());
    }

    public function test_duty_sessions_are_isolated_per_courier(): void
    {
        [$userA, $courierA] = $this->courier('a@example.com');
        [$userB, $courierB] = $this->courier('b@example.com');

        $this->actingAs($userA)->postJson('/api/v1/courier/duty/start')->assertOk();

        // Kurir B belum bertugas walaupun kurir A sudah.
        $this->actingAs($userB)->getJson('/api/v1/courier/duty')
            ->assertOk()
            ->assertJsonPath('data.active', false);

        $this->actingAs($userB)->postJson('/api/v1/courier/duty/start')->assertOk();

        $this->assertSame(2, CourierDutySession::query()->whereNull('ended_at')->count());
        $this->assertNotSame($courierA->id, $courierB->id);
        $this->assertSame(0, CourierDutyLocation::query()->count());
    }

    /**
     * @return array{0: User, 1: Courier}
     */
    private function courier(string $email = 'courier@example.com'): array
    {
        $role = Role::where('name', 'COURIER')->firstOrFail();
        $user = User::factory()->create(['email' => $email, 'role_id' => $role->id]);

        return [$user, Courier::create(['user_id' => $user->id])];
    }
}
