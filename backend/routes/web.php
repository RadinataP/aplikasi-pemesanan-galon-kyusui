
<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\PublicServiceRequestController;
use Illuminate\Session\Middleware\StartSession;
use Illuminate\View\Middleware\ShareErrorsFromSession;
use Illuminate\Foundation\Http\Middleware\PreventRequestForgery;

/*
|--------------------------------------------------------------------------
| Landing Page - Berkah Water
|--------------------------------------------------------------------------
*/

Route::get('/', function () {
    $apk = public_path('downloads/Berkah Water.apk');

    $bytes = is_file($apk) && is_readable($apk)
        ? filesize($apk)
        : false;

    return response()->view('berkahwater', [
        'apkAvailable' => $bytes !== false && $bytes > 0,

        'apkVersion' => $bytes !== false && $bytes > 0
            ? (filemtime($apk) ?: 0)
            : 0,

        'apkSize' => $bytes !== false && $bytes > 0
            ? number_format($bytes / 1048576, 1, ',', '.') . ' MB'
            : null,

    ])->header('Cache-Control', 'no-store');

})->withoutMiddleware([
    StartSession::class,
    ShareErrorsFromSession::class,
    PreventRequestForgery::class,
]);

/*
|--------------------------------------------------------------------------
| Manual Book - Berkah Water
|--------------------------------------------------------------------------
*/

Route::get('/manual-book', function () {
    return redirect('/manual-book.html');
})->name('berkahwater.manual-book');

// Public legal and support pages. Standard web middleware protects the forms with CSRF.
Route::view('/privacy-policy', 'public-info.privacy')->name('berkahwater.privacy-policy');
Route::view('/terms-and-conditions', 'public-info.terms')->name('berkahwater.terms-and-conditions');
Route::view('/support', 'public-info.support', ['requestType' => 'support'])->name('berkahwater.support');
Route::view('/delete-account', 'public-info.delete', ['requestType' => 'delete-account'])->name('berkahwater.delete-account');
Route::post('/requests/{type}', [PublicServiceRequestController::class, 'store'])
    ->whereIn('type', ['support', 'delete-account'])->middleware('throttle:5,10')->name('berkahwater.requests.store');

