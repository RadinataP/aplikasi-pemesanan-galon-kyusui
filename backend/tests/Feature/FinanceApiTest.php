<?php

namespace Tests\Feature;

use App\Models\FinancialTransaction;
use App\Models\Role;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Regression tests untuk buku kas manual.
 * Memastikan pemasukan/pengeluaran tercatat sekali, ringkasan dihitung benar,
 * dan endpoint tidak bisa diakses oleh role selain OWNER.
 */
class FinanceApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        // Route dan model menggunakan role database, bukan string role dari klien.
        foreach (['CUSTOMER', 'OWNER', 'COURIER'] as $name) {
            Role::create([
                'name' => $name,
                'display_name' => Role::defaultDisplayName($name),
            ]);
        }
    }

    private function userWithRole(string $roleName): User
    {
        // Membuat user test yang terhubung ke role resmi dalam database.
        return User::factory()->create([
            'role_id' => Role::where('name', $roleName)->value('id'),
        ]);
    }

    private function manualPayload(array $overrides = []): array
    {
        // Payload tanggal eksplisit agar test tidak bergantung pada timezone saat ini.
        return array_merge([
            'category' => 'Penjualan langsung depot',
            'amount' => '150000.00',
            'payment_method' => 'CASH',
            'description' => 'Penjualan galon kepada warga',
            'occurred_at' => '2026-10-23 09:30:00',
        ], $overrides);
    }

    public function test_owner_can_record_manual_income_outside_order_flow(): void
    {
        $owner = $this->userWithRole('OWNER');

        $response = $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/incomes',
            $this->manualPayload()
        );

        $response->assertCreated()
            ->assertJsonPath('data.type', 'INCOME')
            ->assertJsonPath('data.source', 'MANUAL')
            ->assertJsonPath('data.category', 'Penjualan langsung depot')
            ->assertJsonPath('data.payment_method', 'CASH')
            ->assertJsonPath('data.order_id', null)
            ->assertJsonPath('data.payment_id', null)
            ->assertJsonPath('message', 'Pemasukan berhasil dicatat.');

        // Manual income harus satu baris tanpa referensi order/payment.
        $this->assertDatabaseCount('financial_transactions', 1);
        $this->assertDatabaseHas('financial_transactions', [
            'type' => 'INCOME',
            'source' => 'MANUAL',
            'amount' => '150000.00',
            'order_id' => null,
            'payment_id' => null,
            'recorded_by_user_id' => $owner->id,
        ]);
    }

    public function test_owner_can_record_manual_expense(): void
    {
        $owner = $this->userWithRole('OWNER');

        $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/expenses',
            $this->manualPayload([
                'category' => 'Listrik depot',
                'amount' => '200000',
                'description' => 'Tagihan listrik bulan berjalan',
            ])
        )->assertCreated()
            ->assertJsonPath('data.type', 'EXPENSE')
            ->assertJsonPath('data.source', 'MANUAL')
            ->assertJsonPath('data.category', 'Listrik depot');

        $this->assertDatabaseHas('financial_transactions', [
            'type' => 'EXPENSE',
            'category' => 'Listrik depot',
            'amount' => '200000.00',
            'source' => 'MANUAL',
        ]);
    }

    public function test_finance_endpoints_are_forbidden_to_non_owner_roles(): void
    {
        $customer = $this->userWithRole('CUSTOMER');
        $courier = $this->userWithRole('COURIER');

        foreach ([$customer, $courier] as $user) {
            $this->actingAs($user)->getJson('/api/v1/owner/finance/transactions')->assertForbidden();
            $this->actingAs($user)->postJson('/api/v1/owner/finance/incomes', $this->manualPayload())->assertForbidden();
            $this->actingAs($user)->postJson('/api/v1/owner/finance/expenses', $this->manualPayload())->assertForbidden();
        }

        $this->assertDatabaseCount('financial_transactions', 0);
    }

    public function test_manual_income_and_expense_are_combined_in_period_summary(): void
    {
        $owner = $this->userWithRole('OWNER');

        $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/incomes',
            $this->manualPayload(['amount' => '150000', 'occurred_at' => '2026-10-23 09:30:00'])
        )->assertCreated();

        $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/expenses',
            $this->manualPayload([
                'category' => 'Listrik depot',
                'amount' => '25000',
                'occurred_at' => '2026-10-23 10:00:00',
            ])
        )->assertCreated();

        $this->actingAs($owner)->getJson('/api/v1/owner/finance/transactions?from=2026-10-23&to=2026-10-23')
            ->assertOk()
            ->assertJsonPath('summary.income', '150000.00')
            ->assertJsonPath('summary.expense', '25000.00')
            ->assertJsonPath('summary.net_cash_flow', '125000.00')
            ->assertJsonPath('meta.total', 2);
    }

    public function test_transaction_list_can_filter_source_without_changing_period_summary(): void
    {
        $owner = $this->userWithRole('OWNER');

        FinancialTransaction::create([
            'type' => 'INCOME',
            'category' => 'Penjualan manual',
            'amount' => '100000.00',
            'payment_method' => 'CASH',
            'source' => 'MANUAL',
            'recorded_by_user_id' => $owner->id,
            'description' => 'Manual',
            'occurred_at' => '2026-10-23 09:00:00',
        ]);

        FinancialTransaction::create([
            'type' => 'EXPENSE',
            'category' => 'Listrik',
            'amount' => '20000.00',
            'payment_method' => 'CASH',
            'source' => 'MANUAL',
            'recorded_by_user_id' => $owner->id,
            'description' => 'Expense',
            'occurred_at' => '2026-10-23 10:00:00',
        ]);

        $this->actingAs($owner)->getJson('/api/v1/owner/finance/transactions?source=MANUAL&type=INCOME')
            ->assertOk()
            ->assertJsonPath('meta.total', 1)
            ->assertJsonPath('data.0.type', 'INCOME')
            ->assertJsonPath('summary.income', '100000.00')
            ->assertJsonPath('summary.expense', '20000.00')
            ->assertJsonPath('summary.net_cash_flow', '80000.00');
    }

    public function test_manual_transaction_rejects_zero_amount_or_invalid_payment_method(): void
    {
        $owner = $this->userWithRole('OWNER');

        $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/incomes',
            $this->manualPayload(['amount' => '0'])
        )->assertUnprocessable()->assertJsonValidationErrors(['amount']);

        $this->actingAs($owner)->postJson(
            '/api/v1/owner/finance/expenses',
            $this->manualPayload(['payment_method' => 'MIDTRANS'])
        )->assertUnprocessable()->assertJsonValidationErrors(['payment_method']);

        $this->assertDatabaseCount('financial_transactions', 0);
    }
}
