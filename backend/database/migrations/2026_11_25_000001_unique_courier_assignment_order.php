<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void { Schema::table('courier_assignments', fn (Blueprint $table) => $table->unique('order_id', 'courier_assignments_one_order_unique')); }
    public function down(): void { Schema::table('courier_assignments', fn (Blueprint $table) => $table->dropUnique('courier_assignments_one_order_unique')); }
};
