package com.kyusui.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyusui.app.domain.repository.RegisterRequest
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.InputField
import com.kyusui.app.ui.components.PasswordField
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.theme.KyusuiComponentDimensions

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onLoginClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }

    val state = uiState as? AuthUiState.Unauthenticated
    val isSubmitting = state?.isSubmitting == true
    val errors = state?.fieldErrors.orEmpty()

    val phoneFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }

    fun submitRegistration() {
        viewModel.register(
            RegisterRequest(
                name = name,
                phone = phone,
                email = email,
                password = password,
                passwordConfirmation = passwordConfirmation
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = KyusuiComponentDimensions.AuthContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthBrandHeader(
                title = "Mulai dari sini.",
                subtitle = "Buat akun Berkah Water dalam satu menit.",
                compact = true,
                showBrandEyebrow = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(KyusuiComponentDimensions.AuthCardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AuthSectionIntro(
                        eyebrow = "Pendaftaran pelanggan",
                        title = "Buat akun baru",
                        description = "Isi data berikut agar pesananmu bisa diproses dengan tepat."
                    )

                    state?.errorMessage?.let { InlineError(message = it) }

                    InputField(
                        label = "Nama Lengkap",
                        value = name,
                        onValueChange = { input ->
                            name = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_NAME)
                        },
                        placeholder = "Nama sesuai identitas",
                        leadingIcon = Icons.Outlined.Badge,
                        imeAction = ImeAction.Next,
                        onImeAction = { phoneFocus.requestFocus() },
                        isError = errors.containsKey(AuthViewModel.FIELD_NAME),
                        errorMessage = errors[AuthViewModel.FIELD_NAME],
                        isEnabled = !isSubmitting
                    )

                    InputField(
                        label = "Nomor Telepon",
                        value = phone,
                        onValueChange = { input ->
                            phone = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_PHONE)
                        },
                        modifier = Modifier.focusRequester(phoneFocus),
                        placeholder = "08xxxxxxxxxx",
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = Icons.Outlined.PhoneAndroid,
                        imeAction = ImeAction.Next,
                        onImeAction = { emailFocus.requestFocus() },
                        isError = errors.containsKey(AuthViewModel.FIELD_PHONE),
                        errorMessage = errors[AuthViewModel.FIELD_PHONE],
                        isEnabled = !isSubmitting
                    )

                    InputField(
                        label = "Email",
                        value = email,
                        onValueChange = { input ->
                            email = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_EMAIL)
                        },
                        modifier = Modifier.focusRequester(emailFocus),
                        placeholder = "nama@email.com",
                        keyboardType = KeyboardType.Email,
                        leadingIcon = Icons.Outlined.AlternateEmail,
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordFocus.requestFocus() },
                        isError = errors.containsKey(AuthViewModel.FIELD_EMAIL),
                        errorMessage = errors[AuthViewModel.FIELD_EMAIL],
                        isEnabled = !isSubmitting
                    )

                    PasswordField(
                        label = "Kata Sandi",
                        value = password,
                        onValueChange = { input ->
                            password = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_PASSWORD)
                        },
                        modifier = Modifier.focusRequester(passwordFocus),
                        imeAction = ImeAction.Next,
                        onImeAction = { confirmationFocus.requestFocus() },
                        isError = errors.containsKey(AuthViewModel.FIELD_PASSWORD),
                        errorMessage = errors[AuthViewModel.FIELD_PASSWORD],
                        isEnabled = !isSubmitting
                    )

                    PasswordField(
                        label = "Konfirmasi Kata Sandi",
                        value = passwordConfirmation,
                        onValueChange = { input ->
                            passwordConfirmation = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_PASSWORD_CONFIRMATION)
                        },
                        modifier = Modifier.focusRequester(confirmationFocus),
                        imeAction = ImeAction.Done,
                        onImeAction = ::submitRegistration,
                        isError = errors.containsKey(AuthViewModel.FIELD_PASSWORD_CONFIRMATION),
                        errorMessage = errors[AuthViewModel.FIELD_PASSWORD_CONFIRMATION],
                        isEnabled = !isSubmitting
                    )

                    PrimaryButton(
                        text = "Daftar",
                        onClick = ::submitRegistration,
                        isLoading = isSubmitting,
                        enabled = !isSubmitting,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            AuthFooterText(
                prompt = "Sudah punya akun?",
                action = "Masuk",
                onAction = onLoginClick,
                enabled = !isSubmitting,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
    }
}
