<?php

namespace App\Http\Controllers;

use App\Jobs\SendBerkahPasswordReset;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password as PasswordRule;

class BerkahPasswordResetController extends Controller
{
    private function page(string $mode, array $data = [], int $status = 200)
    {
        return response()->view('berkah-password-reset', ['mode' => $mode] + $data, $status)
            ->header('Cache-Control', 'no-store, private')
            ->header('Referrer-Policy', 'no-referrer')
            ->header('X-Robots-Tag', 'noindex, nofollow')
            ->header('X-Frame-Options', 'DENY')
            ->header('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    }

    public function requestForm() { return $this->page('request'); }

    public function send(Request $request)
    {
        $v = Validator::make($request->all(), ['email' => 'required|string|email|max:255']);
        if ($v->fails()) return $this->page('request', ['error' => 'Masukkan alamat email yang valid.'], 422);
        if (! SendBerkahPasswordReset::mailReady()) return $this->page('request', ['error' => 'Layanan email belum aktif. Hubungi pengelola depot.'], 503);
        $email = trim($v->validated()['email']);
        $key = 'berkah-reset-mail:'.hash('sha256', Str::lower($email));
        // Same response for registered, unknown, inactive and throttled addresses.
        RateLimiter::attempt($key, 3, fn () => SendBerkahPasswordReset::dispatch($email), 900);
        return $this->page('sent');
    }

    public function resetForm(Request $request, string $token)
    {
        $email = $request->query('email');
        if (! is_string($email) || strlen($email) > 255 || ! preg_match('/^[a-fA-F0-9]{64}$/D', $token)) {
            return $this->page('invalid', [], 400);
        }
        return $this->page('reset', compact('email', 'token'));
    }

    public function reset(Request $request)
    {
        $v = Validator::make($request->all(), [
            'email' => 'required|string|email|max:255', 'token' => 'required|string|regex:/^[a-fA-F0-9]{64}$/D',
            'password' => ['required', 'string', 'confirmed', 'max:72', PasswordRule::min(8)->letters()->numbers()->mixedCase(), function ($attribute, $value, $fail) {
                if (is_string($value) && strlen($value) > 72) $fail('Kata sandi maksimal 72 byte.');
            }],
        ]);
        if ($v->fails()) {
            if ($v->errors()->has('email') || $v->errors()->has('token')) return $this->page('invalid', [], 422);
            return $this->page('reset', ['email' => $request->input('email'), 'token' => $request->input('token'), 'error' => 'Kata sandi minimal 8 karakter, berisi huruf besar, huruf kecil dan angka, maksimal 72 byte, serta kedua isian harus sama.'], 422);
        }
        $credentials = $v->validated();
        $status = DB::transaction(function () use ($credentials) {
            $user = User::where('email', $credentials['email'])->where('status', 'ACTIVE')->lockForUpdate()->first();
            if (! $user) return Password::INVALID_TOKEN;
            $credentials['email'] = $user->email;
            $credentials['status'] = 'ACTIVE';
            return Password::broker('users')->reset($credentials, function (User $account, string $password) {
                $account->forceFill(['password' => $password, 'remember_token' => Str::random(60)])->save();
                $account->tokens()->delete();
                if (config('session.driver') === 'database') {
                    DB::table(config('session.table', 'sessions'))->where('user_id', $account->id)->delete();
                }
            });
        });
        return $status === Password::PASSWORD_RESET ? $this->page('success') : $this->page('invalid', [], 422);
    }
}
