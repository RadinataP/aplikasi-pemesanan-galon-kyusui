<?php

use App\Http\Controllers\BerkahPasswordResetController as Recovery;
use Illuminate\Support\Facades\Route;

Route::get('/lupa-kata-sandi', [Recovery::class, 'requestForm'])->name('berkah.password.request');
Route::post('/lupa-kata-sandi', [Recovery::class, 'send'])->middleware('throttle:20,1')->name('berkah.password.email');
Route::get('/atur-ulang-kata-sandi/{token}', [Recovery::class, 'resetForm'])->name('berkah.password.reset');
Route::post('/atur-ulang-kata-sandi', [Recovery::class, 'reset'])->middleware('throttle:20,1')->name('berkah.password.update');
