@if(session('receipt'))<div class="legal-notice" role="status"><strong>Permintaan berhasil dicatat.</strong><p>Nomor referensi: {{ session('receipt') }}. Simpan nomor ini. Permintaan menunggu pemeriksaan pengelola.</p></div>@endif
@if($errors->any())<div class="legal-notice legal-error" role="alert">Periksa kembali data yang diisi.<ul>@foreach($errors->all() as $error)<li>{{ $error }}</li>@endforeach</ul></div>@endif
<form class="legal-form" method="POST" action="{{ route('berkahwater.requests.store', ['type' => $requestType]) }}">
@csrf
<label for="email">Email {{ $requestType === 'delete-account' ? 'akun Berkah Water' : 'yang dapat dihubungi' }}</label><input id="email" name="email" type="email" maxlength="254" autocomplete="email" required value="{{ old('email') }}">
<label for="message">{{ $requestType === 'delete-account' ? 'Keterangan (opsional)' : 'Jelaskan kendala Anda' }}</label><textarea id="message" name="message" rows="5" maxlength="1500" @if($requestType === 'support') required @endif>{{ old('message') }}</textarea>
<p>Jangan memasukkan kata sandi, OTP, PIN, bukti pembayaran atau informasi sensitif lainnya.</p>
<label class="legal-check"><input type="checkbox" name="confirmation" value="1" required><span>Saya memahami bahwa data ini akan diproses untuk menangani permintaan sesuai <a href="/privacy-policy">Kebijakan Privasi</a>.</span></label>
<button type="submit">Kirim permintaan</button></form>
