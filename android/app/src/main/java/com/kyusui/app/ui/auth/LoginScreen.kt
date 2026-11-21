package com.kyusui.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.InputField
import com.kyusui.app.ui.components.PasswordField
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.TextButton
import com.kyusui.app.ui.theme.KyusuiComponentDimensions

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val state = uiState as? AuthUiState.Unauthenticated
    val isSubmitting = state?.isSubmitting == true
    val emailError = state?.fieldErrors?.get(AuthViewModel.FIELD_EMAIL)
    val passwordError = state?.fieldErrors?.get(AuthViewModel.FIELD_PASSWORD)
    val passwordFocus = remember { FocusRequester() }

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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthBrandHeader(
                title = "Segarkan harimu.",
                subtitle = "Air berkualitas, dalam beberapa langkah.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AuthSectionIntro(
                        eyebrow = "Selamat datang kembali",
                        title = "Masuk ke akunmu",
                        description = "Lanjutkan pemesanan air minum dengan mudah."
                    )

                    state?.errorMessage?.let { InlineError(message = it) }

                    InputField(
                        label = "Email",
                        value = email,
                        onValueChange = { input ->
                            email = input
                            viewModel.clearFieldError(AuthViewModel.FIELD_EMAIL)
                        },
                        placeholder = "nama@email.com",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordFocus.requestFocus() },
                        leadingIcon = Icons.Outlined.AlternateEmail,
                        isError = emailError != null,
                        errorMessage = emailError,
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
                        imeAction = ImeAction.Done,
                        onImeAction = { viewModel.login(email, password) },
                        isError = passwordError != null,
                        errorMessage = passwordError,
                        isEnabled = !isSubmitting
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            text = "Lupa kata sandi?",
                            onClick = onForgotPasswordClick,
                            enabled = !isSubmitting
                        )
                    }

                    PrimaryButton(
                        text = "Masuk",
                        onClick = { viewModel.login(email, password) },
                        isLoading = isSubmitting,
                        enabled = !isSubmitting,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            AuthFooterText(
                prompt = "Belum punya akun?",
                action = "Daftar",
                onAction = onRegisterClick,
                enabled = !isSubmitting,
                modifier = Modifier.padding(top = 18.dp)
            )

            Spacer(Modifier.height(4.dp))
            TextButton(
                text = "Tentang Berkah Water",
                onClick = onAboutClick,
                enabled = !isSubmitting
            )
        }
    }
}
