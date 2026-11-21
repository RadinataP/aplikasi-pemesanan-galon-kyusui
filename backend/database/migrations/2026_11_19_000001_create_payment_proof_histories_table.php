<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('payment_proof_histories', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('payment_id')->constrained()->cascadeOnDelete();
            $table->string('proof_image')->nullable();
            $table->unsignedBigInteger('proof_size')->nullable();
            $table->string('proof_mime_type', 100)->nullable();
            $table->string('proof_checksum', 64)->nullable();
            $table->string('decision', 20);
            $table->string('note', 500)->nullable();
            $table->foreignId('actor_user_id')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('decided_at');
            $table->timestamps();
            $table->index(['payment_id', 'decided_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('payment_proof_histories');
    }
};
