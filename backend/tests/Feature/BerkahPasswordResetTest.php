<?php

namespace Tests\Feature;

use App\Jobs\SendBerkahPasswordReset;
use App\Models\Role;
use App\Models\User;
use App\Notifications\BerkahPasswordReset;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Facades\Queue;
use Tests\TestCase;

class BerkahPasswordResetTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        config(['app.url' => 'https://depot.berkahwater.my.id', 'mail.default' => 'smtp',
            'mail.mailers.smtp.username' => 'test@example.test', 'mail.mailers.smtp.password' => 'fake-password']);
    }

    private function account(string $role = 'CUSTOMER'): User
    {
        return User::factory()->create(['role_id' => Role::firstOrCreate(['name' => $role],
            ['display_name' => Role::defaultDisplayName($role)])->id]);
    }

    public function test_request_does_not_reveal_account_and_limits_email_requests(): void
    {
        Queue::fake();
        $user = $this->account();
        $this->get('/lupa-kata-sandi')->assertOk()->assertSee('Kirim tautan pemulihan');
        foreach ([$user->email, $user->email, $user->email, $user->email, 'unknown@example.test'] as $email) {
            $this->post('/lupa-kata-sandi', ['email' => $email])->assertOk()->assertSee('Jika email tersebut terdaftar');
        }
        Queue::assertPushed(SendBerkahPasswordReset::class, 4);
    }

    public function test_log_mailer_cannot_send_reset_tokens(): void
    {
        config(['mail.default' => 'log']);
        Queue::fake();
        $this->post('/lupa-kata-sandi', ['email' => 'test@example.test'])->assertStatus(503);
        Queue::assertNothingPushed();
    }

    public function test_email_links_reset_all_roles_once_and_revoke_api_tokens(): void
    {
        Notification::fake();
        foreach (['CUSTOMER', 'COURIER', 'OWNER'] as $role) {
            $user = $this->account($role);
            $user->createToken('old-device');
            (new SendBerkahPasswordReset($user->email))->handle();
            $notification = Notification::sent($user, BerkahPasswordReset::class)->first();
            $this->assertNotNull($notification);
            $this->assertStringStartsWith('https://depot.berkahwater.my.id/atur-ulang-kata-sandi/', $notification->toMail($user)->actionUrl);
            $data = ['email' => $user->email, 'token' => $notification->token, 'password' => 'NewPassword123', 'password_confirmation' => 'NewPassword123'];
            $this->post('/atur-ulang-kata-sandi', $data)->assertOk()->assertSee('Kata sandi diperbarui');
            $this->assertTrue(Hash::check('NewPassword123', $user->fresh()->password));
            $this->assertSame(0, $user->tokens()->count());
            $this->post('/atur-ulang-kata-sandi', $data)->assertStatus(422)->assertSee('Tautan tidak berlaku');
        }
    }

    public function test_invalid_and_expired_tokens_cannot_change_password(): void
    {
        $user = $this->account();
        $data = ['email' => $user->email, 'token' => str_repeat('a', 64), 'password' => 'NewPassword123', 'password_confirmation' => 'NewPassword123'];
        $this->post('/atur-ulang-kata-sandi', $data)->assertStatus(422);
        $data['token'] = Password::broker('users')->createToken($user);
        $this->travel(61)->minutes();
        $this->post('/atur-ulang-kata-sandi', $data)->assertStatus(422);
        $this->assertTrue(Hash::check('password', $user->fresh()->password));
    }
}
