<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>Pemulihan akun | Berkah Water</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#eff7f9;color:#15374a;font:16px/1.6 system-ui,sans-serif}main{width:min(100% - 32px,480px);margin:clamp(24px,8vh,88px) auto}header{padding:24px 26px;border-radius:24px 24px 0 0;background:linear-gradient(125deg,#07384a,#087e97);color:white}header small{letter-spacing:.16em;color:#b6e5ed;font-weight:700}h1{font-size:clamp(25px,6vw,32px);line-height:1.15;margin:12px 0 6px}section{background:#fff;border-radius:0 0 24px 24px;padding:26px;box-shadow:0 14px 45px #07384a15}p{margin:0 0 18px}label{display:block;font-weight:650;margin:12px 0 5px}input{font:inherit;border:1px solid #b5cbd3;border-radius:12px;padding:12px;width:100%;color:inherit}input:focus{outline:2px solid #087e97;outline-offset:2px}button,.btn{display:block;width:100%;margin:20px 0 12px;border:0;border-radius:12px;background:#087e97;color:white;padding:14px;font:inherit;font-weight:700;text-align:center;text-decoration:none;cursor:pointer}a{color:#07687b}small,.muted{color:#57717b}.error{background:#fff0eb;padding:12px;border-radius:10px;color:#862c20}.note{background:#e9f6f8;padding:14px;border-radius:12px}footer{text-align:center;margin:18px;font-size:13px;color:#57717b}</style></head><body><main><header><small>BERKAH WATER · AKUN</small><h1>{{ match($mode) {'reset'=>'Kata sandi baru','sent'=>'Periksa email Anda','success'=>'Kata sandi diperbarui','invalid'=>'Tautan tidak berlaku',default=>'Lupa kata sandi?'} }}</h1>Pemulihan akses akun</header><section>
@if(isset($error))<p class="error" role="alert">{{ $error }}</p>@endif
@if($mode === 'request')
<p>Masukkan email yang terdaftar pada akun Anda. Kami akan mengirim tautan untuk membuat kata sandi baru.</p>
<form method="post" action="/lupa-kata-sandi">@csrf<label for="email">Alamat email</label><input id="email" name="email" type="email" autocomplete="email" maxlength="255" required placeholder="nama@email.com"><button type="submit">Kirim tautan pemulihan</button></form>
@elseif($mode === 'sent')
<p>Jika email tersebut terdaftar dan akun aktif, tautan pemulihan akan dikirim. Periksa kotak masuk dan folder Spam.</p><p class="note">Jangan membagikan tautan. Jika belum menerima email, coba lagi beberapa menit kemudian.</p><a href="/lupa-kata-sandi">Kembali ke formulir</a>
@elseif($mode === 'reset')
<p>Atur kata sandi baru untuk akun <strong>{{ $email }}</strong>.</p>
<form method="post" action="/atur-ulang-kata-sandi">@csrf<input type="hidden" name="email" value="{{ $email }}"><input type="hidden" name="token" value="{{ $token }}"><label for="password">Kata sandi baru</label><input type="password" id="password" name="password" autocomplete="new-password" minlength="8" maxlength="72" required><small>Minimal 8 karakter, huruf besar, huruf kecil dan angka.</small><label for="confirmation">Ulangi kata sandi baru</label><input type="password" id="confirmation" name="password_confirmation" autocomplete="new-password" minlength="8" maxlength="72" required><button type="submit">Simpan kata sandi</button></form>
@elseif($mode === 'success')
<p>Kata sandi Anda berhasil diubah. Sesi aplikasi lama telah dicabut. Silakan kembali ke aplikasi Berkah Water dan masuk dengan kata sandi baru.</p><a class="btn" href="/">Kembali ke website</a>
@else
<p>Tautan salah, sudah digunakan, atau kedaluwarsa. Ajukan permintaan pemulihan baru.</p><a class="btn" href="/lupa-kata-sandi">Minta tautan baru</a>
@endif
</section><footer><a href="/">Berkah Water</a> · Jangan bagikan kata sandi Anda.</footer></main></body></html>
