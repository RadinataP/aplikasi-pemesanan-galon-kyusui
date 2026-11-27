<?php
namespace App\Http\Controllers;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Http\RedirectResponse;
class PublicServiceRequestController extends Controller
{
    public function store(Request $request, string $type): RedirectResponse
    {
        abort_unless(in_array($type, ['support', 'delete-account'], true), 404);
        $data = $request->validate([
            'email' => ['required', 'email:rfc', 'max:254'],
            'message' => [$type === 'support' ? 'required' : 'nullable', 'string', 'max:1500'],
            'confirmation' => ['accepted'],
        ]);
        $id = (string) Str::uuid();
        DB::table('public_service_requests')->insert([
            'id' => $id, 'type' => $type, 'status' => 'pending',
            'email' => Crypt::encryptString($data['email']),
            'message' => isset($data['message']) ? Crypt::encryptString($data['message']) : null,
            'created_at' => now(), 'updated_at' => now(),
        ]);
        // Never reveal whether an account exists. An operator verifies ownership separately.
        return redirect()->route('berkahwater.'.$type)->with('receipt', $id);
    }
}
