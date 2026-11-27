<?php

namespace App\Notifications;

use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

class BerkahPasswordReset extends Notification
{
    public function __construct(public string $token) {}

    public function via(object $notifiable): array { return ['mail']; }

    public function toMail(object $notifiable): MailMessage
    {
        $url = rtrim(config('app.url'), '/').route('berkah.password.reset', [
            'token' => $this->token, 'email' => $notifiable->getEmailForPasswordReset(),
        ], false);
        return (new MailMessage)->subject('Atur ulang kata sandi Berkah Water')
            ->greeting('Halo!')
            ->line('Kami menerima permintaan pemulihan akun Berkah Water Anda.')
            ->action('Atur ulang kata sandi', $url)
            ->line('Tautan berlaku selama '.config('auth.passwords.users.expire', 60).' menit dan hanya dapat digunakan sekali.')
            ->line('Jika Anda tidak meminta pemulihan, abaikan email ini. Jangan bagikan tautan kepada siapa pun.')
            ->salutation('Berkah Water');
    }
}
