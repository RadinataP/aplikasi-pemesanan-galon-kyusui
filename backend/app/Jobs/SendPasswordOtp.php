<?php

namespace App\Jobs;

use App\Models\User;
use Illuminate\Contracts\Queue\ShouldBeEncrypted;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;

class SendPasswordOtp implements ShouldQueue, ShouldBeEncrypted
{
    use Queueable;
    public int $tries = 1;
    public int $timeout = 60;

    public function __construct(public string $email, public int $requestedAt) {}

    public static function mailReady(): bool
    {
        return config('mail.default') === 'smtp'
            && filled(config('mail.mailers.smtp.host'))
            && filled(config('mail.mailers.smtp.username'))
            && filled(config('mail.mailers.smtp.password'))
            && filled(config('mail.from.address'))
            && str_starts_with((string) config('app.url'), 'https://');
    }

    public function handle(): void
    {
        if (! self::mailReady()) {
            Log::warning('Berkah Water OTP: SMTP belum dikonfigurasi.');
            return;
        }
        // Abaikan permintaan lama setelah worker berhenti terlalu lama.
        if (now()->timestamp - $this->requestedAt > 300) return;
        $delivery = DB::transaction(function () {
            $user = User::where('email', $this->email)->where('status', 'ACTIVE')->lockForUpdate()->first();
            if (! $user) return null;
            $query = DB::table('password_reset_otps')->where('user_id', $user->id);
            $old = $query->lockForUpdate()->first();
            if ($old && \Illuminate\Support\Carbon::parse($old->issued_at)->gt(now()->subSeconds(60))) return null;
            $code = str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
            $hash = Hash::make($code);
            DB::table('password_reset_otps')->updateOrInsert(['user_id' => $user->id], [
                'code_hash' => $hash, 'attempts' => 0, 'issued_at' => now(), 'expires_at' => now()->addMinutes(10),
            ]);
            return [$user->id, $user->email, $code, $hash];
        });
        if (! $delivery) return;
        [$id, $email, $code, $hash] = $delivery;
        try {
            Mail::mailer('smtp')->raw("Kode pemulihan akun Berkah Water: {$code}\n\nKode berlaku 10 menit dan hanya dapat digunakan sekali.\nMasukkan kode ini di aplikasi Berkah Water. Jangan bagikan kode ini.\nJika Anda tidak meminta reset, abaikan email ini.",
                fn ($message) => $message->to($email)->subject('Kode pemulihan akun Berkah Water'));
        } catch (\Throwable $e) {
            DB::table('password_reset_otps')->where('user_id', $id)->where('code_hash', $hash)->delete();
            // Jangan log isi email, OTP, alamat atau kredensial SMTP.
            Log::warning('Berkah Water OTP: pengiriman SMTP gagal.', ['type' => get_class($e)]);
        }
    }
}
