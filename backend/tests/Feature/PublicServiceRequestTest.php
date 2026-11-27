<?php
namespace Tests\Feature;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;
class PublicServiceRequestTest extends TestCase
{
    use RefreshDatabase;
    protected function setUp(): void {
        parent::setUp();
        config(['app.key' => 'base64:'.base64_encode(str_repeat('t', 32))]);
    }
    public function test_public_information_pages_are_accessible(): void {
        foreach (['/privacy-policy','/terms-and-conditions','/support','/delete-account'] as $path) {
            $this->get($path)->assertOk();
        }
    }
    public function test_deletion_request_is_recorded_without_deleting_account(): void {
        $role = \App\Models\Role::firstOrCreate(['name'=>'CUSTOMER'], ['display_name'=>'Customer']);
        $user = \App\Models\User::factory()->create(['role_id'=>$role->id]);
        $this->post('/requests/delete-account', ['email'=>$user->email,'confirmation'=>'1'])->assertRedirect('/delete-account')->assertSessionHas('receipt');
        $row = DB::table('public_service_requests')->first();
        $this->assertSame('pending', $row->status);
        $this->assertSame($user->email, Crypt::decryptString($row->email));
        $this->assertDatabaseHas('users', ['id'=>$user->id]);
    }
    public function test_support_request_requires_valid_email_message_and_confirmation(): void {
        $this->post('/requests/support', ['email'=>'invalid'])->assertSessionHasErrors(['email','message','confirmation']);
        $this->assertDatabaseCount('public_service_requests', 0);
    }
}
