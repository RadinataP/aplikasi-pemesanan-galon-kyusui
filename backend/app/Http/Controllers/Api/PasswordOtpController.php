<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Jobs\SendPasswordOtp;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password;

class PasswordOtpController extends Controller
{
    public function send(Request $request): JsonResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email', 'max:255']]);
        if (! SendPasswordOtp::mailReady() || config('queue.default') !== 'database') {
            return response()->json(['message' => 'Layanan pemulihan belum aktif. Hubungi pengelola depot.'], 503);
        }
        $email = trim($data['email']);
        $key = 'bw-otp-send:'.hash('sha256', Str::lower($email));
        // Respon sama untuk akun aktif, tidak aktif, tidak ditemukan, atau dibatasi.
        RateLimiter::attempt($key.':cooldown', 1, function () use ($email, $key) {
            RateLimiter::attempt($key, 3, function () use ($email) {
                SendPasswordOtp::dispatch($email, now()->timestamp);
            }, 900);
        }, 60);
        return response()->json(['message' => 'Jika akun memenuhi syarat, kode akan dikirim ke email.'], 202);
    }

    public function reset(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email', 'max:255'],
            'code' => ['required', 'string', 'regex:/^[0-9]{6}$/D'],
            'password' => ['required', 'string', 'confirmed', 'max:72', Password::min(8)->letters()->mixedCase()->numbers(),
                function ($attribute, $value, $fail) {
                    if (is_string($value) && strlen($value) > 72) $fail('Kata sandi maksimal 72 byte.');
                }],
        ]);
        $key = 'bw-otp-verify:'.hash('sha256', Str::lower($data['email']));
        $ok = RateLimiter::attempt($key, 15, function () use ($data) {
            // Lock user sebelum OTP: urutan lock sama dengan job pengiriman.
            return DB::transaction(function () use ($data) {
                $user = User::where('email', trim($data['email']))->where('status', 'ACTIVE')->lockForUpdate()->first();
                if (! $user) return false;
                $query = DB::table('password_reset_otps')->where('user_id', $user->id);
                $otp = $query->lockForUpdate()->first();
                if (! $otp || now()->gte(\Illuminate\Support\Carbon::parse($otp->expires_at)) || $otp->attempts >= 5) return false;
                if (! Hash::check($data['code'], $otp->code_hash)) {
                    $query->increment('attempts');
                    return false;
                }
                $user->forceFill(['password' => $data['password'], 'remember_token' => Str::random(60)])->save();
                $user->tokens()->delete();
                DB::table('password_reset_tokens')->where('email', $user->email)->delete();
                if (config('session.driver') === 'database') {
                    DB::table(config('session.table', 'sessions'))->where('user_id', $user->id)->delete();
                }
                $query->delete();
                return true;
            });
        }, 900);
        return $ok === true
            ? response()->json(['message' => 'Kata sandi berhasil diubah. Silakan masuk kembali.'])
            : response()->json(['message' => 'Kode tidak valid, kedaluwarsa, atau batas percobaan tercapai. Minta kode baru jika diperlukan.'], 422);
    }
}
