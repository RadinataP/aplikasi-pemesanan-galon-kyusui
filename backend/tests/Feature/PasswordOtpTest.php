<?php
namespace Tests\Feature;

use App\Jobs\SendPasswordOtp;
use App\Models\Role;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Bus;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class PasswordOtpTest extends TestCase
{
    use RefreshDatabase;

    private function account(string $role = 'CUSTOMER'): User
    {
        $r = Role::create(['name' => $role, 'display_name' => Role::defaultDisplayName($role)]);
        return User::factory()->create(['role_id' => $r->id, 'status' => 'ACTIVE']);
    }
    private function otp(User $user, int $attempts = 0, bool $expired = false): void
    {
        DB::table('password_reset_otps')->insert([
            'user_id' => $user->id, 'code_hash' => Hash::make('012345'), 'attempts' => $attempts,
            'issued_at' => now()->subMinutes(2), 'expires_at' => $expired ? now()->subSecond() : now()->addMinutes(8),
        ]);
    }
    private function reset(User $user, string $code = '012345')
    {
        return $this->postJson('/api/v1/auth/reset-password', [
            'email' => $user->email, 'code' => $code, 'password' => 'NewPassword123', 'password_confirmation' => 'NewPassword123',
        ]);
    }
    public function test_request_is_generic_and_queued(): void
    {
        config(['mail.default' => 'smtp', 'mail.mailers.smtp.host' => 'smtp.gmail.com',
            'mail.mailers.smtp.username' => 'sender@example.com', 'mail.mailers.smtp.password' => 'test',
            'mail.from.address' => 'sender@example.com', 'app.url' => 'https://example.com', 'queue.default' => 'database']);
        Bus::fake();
        $user = $this->account();
        $a = $this->postJson('/api/v1/auth/forgot-password', ['email' => $user->email])->assertStatus(202);
        $b = $this->postJson('/api/v1/auth/forgot-password', ['email' => 'unknown@example.net'])->assertStatus(202);
        $this->assertSame($a->json(), $b->json());
        Bus::assertDispatched(SendPasswordOtp::class, 2);
    }
    public function test_reset_for_every_role_and_token_revocation(): void
    {
        foreach (['CUSTOMER', 'COURIER', 'OWNER'] as $role) {
            $user = $this->account($role);
            $user->createToken('old');
            $this->otp($user);
            $this->reset($user)->assertOk();
            $this->assertTrue(Hash::check('NewPassword123', $user->fresh()->password));
            $this->assertSame(0, $user->tokens()->count());
            $this->reset($user)->assertStatus(422);
        }
    }
    public function test_fifth_wrong_code_blocks_even_correct_code(): void
    {
        $user = $this->account();
        $this->otp($user, 4);
        $this->reset($user, '999999')->assertStatus(422);
        $this->assertDatabaseHas('password_reset_otps', ['user_id' => $user->id, 'attempts' => 5]);
        $this->reset($user)->assertStatus(422);
    }
    public function test_expired_code_is_rejected(): void
    {
        $user = $this->account();
        $this->otp($user, 0, true);
        $this->reset($user)->assertStatus(422);
    }
}
