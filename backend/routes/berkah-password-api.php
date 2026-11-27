<?php

use App\Http\Controllers\Api\PasswordOtpController;
use Illuminate\Support\Facades\Route;

// File ini di-require pada akhir routes/api.php, di luar group auth:sanctum.
Route::prefix('v1/auth')->group(function () {
    Route::post('forgot-password', [PasswordOtpController::class, 'send'])
        ->middleware('throttle:15,1')->name('api.v1.auth.forgot-password');
    Route::post('reset-password', [PasswordOtpController::class, 'reset'])
        ->middleware('throttle:30,1')->name('api.v1.auth.reset-password');
});
