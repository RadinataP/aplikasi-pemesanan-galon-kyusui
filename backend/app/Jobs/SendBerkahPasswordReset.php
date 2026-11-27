<?php

namespace App\Jobs;

use App\Models\User;
use App\Notifications\BerkahPasswordReset;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Password;

class SendBerkahPasswordReset implements ShouldQueue
{
    use Queueable;

    public int $tries = 1;
    public int $timeout = 60;

    public function __construct(public string $email) {}

    public static function mailReady(): bool
    {
        return config('mail.default') === 'smtp'
            && filled(config('mail.mailers.smtp.username'))
            && filled(config('mail.mailers.smtp.password'))
            && str_starts_with((string) config('app.url'), 'https://');
    }

    public function handle(): void
    {
        // Never send a live reset token to the log mailer.
        if (! self::mailReady()) {
            Log::warning('Berkah Water password reset mail is not configured.');
            return;
        }

        $delivery = DB::transaction(function () {
            $user = User::where('email', $this->email)->where('status', 'ACTIVE')->lockForUpdate()->first();
            if (! $user) return null;
            $delivery = null;
            Password::broker('users')->sendResetLink(['email' => $user->email, 'status' => 'ACTIVE'],
                function (User $recipient, string $token) use (&$delivery) {
                    $delivery = [$recipient, $token];
                });
            return $delivery;
        });

        if (! $delivery) return;
        try {
            $delivery[0]->notify(new BerkahPasswordReset($delivery[1]));
        } catch (\Throwable $e) {
            // Do not write email contents, token, or SMTP credentials to logs.
            Log::warning('Berkah Water password reset email delivery failed.', ['type' => get_class($e)]);
        }
    }
}
