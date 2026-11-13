<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\FinancialTransaction;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * API Buku Kas Digital KYUSUI.
 *
 * Seluruh operasi hanya untuk OWNER. Pemasukan order aplikasi tidak dicatat
 * melalui endpoint manual: Payment::transitionTo(PAID) sudah membuat transaksi
 * income otomatis dan menggunakan payment_id unik untuk mencegah duplikasi.
 */
class FinanceController extends Controller
{
    /**
     * Mengembalikan transaksi dan ringkasan untuk periode yang dipilih.
     * Summary sengaja tidak terpengaruh filter type/source; keduanya selalu
     * menghitung seluruh income dan expense dalam periode tanggal yang sama.
     */
    public function index(Request $request): JsonResponse
    {
        $this->authorizeOwner($request);

        // Validasi filter agar query hanya menerima nilai yang didukung API.
        $filters = $request->validate([
            'from' => ['sometimes', 'date'],
            'to' => ['sometimes', 'date', 'after_or_equal:from'],
            'type' => ['sometimes', 'in:INCOME,EXPENSE'],
            'source' => ['sometimes', 'in:APP_PAYMENT,DIRECT_ORDER,MANUAL'],
            'page' => ['sometimes', 'integer', 'min:1'],
            'per_page' => ['sometimes', 'integer', 'min:1', 'max:100'],
        ]);

        // Filter tanggal dipakai bersama oleh ringkasan dan daftar transaksi.
        $periodQuery = FinancialTransaction::query()
            ->when(isset($filters['from']), fn ($q) => $q->whereDate('occurred_at', '>=', $filters['from']))
            ->when(isset($filters['to']), fn ($q) => $q->whereDate('occurred_at', '<=', $filters['to']));

        // Gunakan clone agar perhitungan income tidak mengubah query expense.
        $income = (clone $periodQuery)->where('type', 'INCOME')->sum('amount');
        $expense = (clone $periodQuery)->where('type', 'EXPENSE')->sum('amount');

        // Filter type/source hanya menyaring baris riwayat, bukan kartu ringkasan.
        $rows = (clone $periodQuery)
            ->with(['recorder:id,name'])
            ->when(isset($filters['type']), fn ($q) => $q->where('type', $filters['type']))
            ->when(isset($filters['source']), fn ($q) => $q->where('source', $filters['source']))
            ->orderByDesc('occurred_at')
            ->orderByDesc('id')
            ->paginate($filters['per_page'] ?? 20);

        return response()->json([
            'data' => $rows->getCollection()->map(fn (FinancialTransaction $row) => $this->serialize($row))->values(),
            'summary' => [
                'income' => number_format((float) $income, 2, '.', ''),
                'expense' => number_format((float) $expense, 2, '.', ''),
                'net_cash_flow' => number_format((float) $income - (float) $expense, 2, '.', ''),
            ],
            'meta' => [
                'current_page' => $rows->currentPage(),
                'per_page' => $rows->perPage(),
                'total' => $rows->total(),
                'last_page' => $rows->lastPage(),
            ],
            'message' => 'Buku kas berhasil dimuat.',
        ]);
    }

    /**
     * Catat pengeluaran manual seperti listrik, perawatan pompa, atau bensin.
     * Jangan gunakan method ini untuk income ataupun pembayaran order.
     */
    public function storeExpense(Request $request): JsonResponse
    {
        $this->authorizeOwner($request);
        $data = $this->validateManualTransaction($request);

        // DB::transaction memastikan penyimpanan satu transaksi bersifat atomik.
        $row = DB::transaction(fn () => FinancialTransaction::create([
            'type' => 'EXPENSE',
            'category' => trim($data['category']),
            'amount' => number_format((float) $data['amount'], 2, '.', ''),
            'payment_method' => $data['payment_method'],
            'source' => 'MANUAL',
            'recorded_by_user_id' => $request->user()->id,
            'description' => $this->cleanDescription($data['description'] ?? null),
            'occurred_at' => $data['occurred_at'],
        ]));

        // Muat nama pencatat agar respons sama bentuknya dengan endpoint index.
        $row->load('recorder:id,name');

        return response()->json([
            'data' => $this->serialize($row),
            'message' => 'Pengeluaran berhasil dicatat.',
        ], 201);
    }

    /**
     * Catat pemasukan MANUAL di luar sistem, misalnya penjualan tunai kepada
     * warga yang tidak membuat order melalui aplikasi pelanggan.
     *
     * Jangan panggil endpoint ini untuk direct-order yang telah tersimpan
     * melalui DirectOrderController; order tersebut sudah membukukan income
     * otomatis saat payment bertransisi ke PAID.
     */
    public function storeIncome(Request $request): JsonResponse
    {
        $this->authorizeOwner($request);
        $data = $this->validateManualTransaction($request);

        // Manual income tidak memiliki order_id/payment_id karena bukan order.
        // Pencatatan QRIS manual hanya boleh dilakukan setelah penerimaan benar
        // benar diperiksa; ini bukan endpoint verifikasi QRIS order aplikasi.
        $row = DB::transaction(fn () => FinancialTransaction::create([
            'type' => 'INCOME',
            'category' => trim($data['category']),
            'amount' => number_format((float) $data['amount'], 2, '.', ''),
            'payment_method' => $data['payment_method'],
            'source' => 'MANUAL',
            'order_id' => null,
            'payment_id' => null,
            'recorded_by_user_id' => $request->user()->id,
            'reversal_of_id' => null,
            'description' => $this->cleanDescription($data['description'] ?? null),
            'occurred_at' => $data['occurred_at'],
        ]));

        $row->load('recorder:id,name');

        return response()->json([
            'data' => $this->serialize($row),
            'message' => 'Pemasukan berhasil dicatat.',
        ], 201);
    }

    /**
     * Aturan input manual dipusatkan agar validasi pemasukan/pengeluaran sama.
     * Backend menyimpan nominal desimal dua digit; Android hanya mengirim nilai.
     */
    private function validateManualTransaction(Request $request): array
    {
        return $request->validate([
            'category' => ['required', 'string', 'max:60'],
            'amount' => ['required', 'numeric', 'gt:0', 'max:999999999999.99'],
            'payment_method' => ['required', 'in:CASH,QRIS,OTHER'],
            'description' => ['nullable', 'string', 'max:500'],
            'occurred_at' => ['required', 'date'],
        ]);
    }

    /** Defense in depth di samping middleware role:OWNER pada routes/api.php. */
    private function authorizeOwner(Request $request): void
    {
        abort_unless($request->user()?->role?->name === 'OWNER', 403, 'Akses hanya untuk owner.');
    }

    /** String keterangan kosong dinormalisasi menjadi null, bukan string kosong. */
    private function cleanDescription(?string $description): ?string
    {
        $description = trim((string) $description);
        return $description === '' ? null : $description;
    }

    /** Serialisasi kontrak API yang digunakan Android; tidak membocorkan path privat. */
    private function serialize(FinancialTransaction $row): array
    {
        return [
            'id' => $row->id,
            'type' => $row->type,
            'category' => $row->category,
            'amount' => $row->amount,
            'payment_method' => $row->payment_method,
            'source' => $row->source,
            'order_id' => $row->order_id,
            'payment_id' => $row->payment_id,
            'recorded_by' => $row->recorder?->name,
            'description' => $row->description,
            'occurred_at' => $row->occurred_at?->toISOString(),
            'created_at' => $row->created_at?->toISOString(),
        ];
    }
}
