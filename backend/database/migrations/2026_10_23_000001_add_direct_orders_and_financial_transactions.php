<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Pesanan langsung tidak selalu memiliki akun Customer di aplikasi.
        // Customer ID dibuat nullable, sedangkan snapshot nama/telepon tetap
        // menyimpan identitas pelanggan untuk keperluan riwayat dan struk.
        Schema::table('orders', function (Blueprint $table): void {
            $table->foreignId('customer_id')->nullable()->change();
            $table->string('source', 24)->default('APP')->after('customer_id');
            $table->foreignId('created_by_user_id')->nullable()->after('source')
                ->constrained('users')->nullOnDelete();
            $table->string('customer_name', 120)->nullable()->after('created_by_user_id');
            $table->string('customer_phone', 40)->nullable()->after('customer_name');
            $table->string('fulfillment_type', 20)->default('DELIVERY')->after('customer_phone');
            $table->index(['source', 'placed_at']);
        });

        // Satu buku kas untuk pemasukan dan pengeluaran aktual. payment_id
        // unik mencegah pemasukan dari payment yang sama tercatat dua kali.
        Schema::create('financial_transactions', function (Blueprint $table): void {
            $table->id();
            $table->string('type', 12); // INCOME atau EXPENSE
            $table->string('category', 60);
            $table->decimal('amount', 15, 2);
            $table->string('payment_method', 12)->nullable(); // QRIS / CASH / OTHER
            $table->string('source', 24); // APP_PAYMENT / DIRECT_ORDER / MANUAL
            $table->foreignId('order_id')->nullable()->constrained('orders')->nullOnDelete();
            $table->foreignId('payment_id')->nullable()->unique()->constrained('payments')->nullOnDelete();
            $table->foreignId('recorded_by_user_id')->nullable()->constrained('users')->nullOnDelete();
            $table->foreignId('reversal_of_id')->nullable()->constrained('financial_transactions')->nullOnDelete();
            $table->string('description', 500)->nullable();
            $table->timestamp('occurred_at');
            $table->timestamps();
            $table->index(['type', 'occurred_at']);
            $table->index(['source', 'occurred_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('financial_transactions');
        Schema::table('orders', function (Blueprint $table): void {
            $table->dropIndex(['source', 'placed_at']);
            $table->dropConstrainedForeignId('created_by_user_id');
            $table->dropColumn(['source', 'customer_name', 'customer_phone', 'fulfillment_type']);
            // Rollback migration harus mengembalikan non-null. Pastikan semua
            // pesanan langsung dihapus/diubah terlebih dahulu sebelum rollback.
            $table->foreignId('customer_id')->nullable(false)->change();
        });
    }
};
