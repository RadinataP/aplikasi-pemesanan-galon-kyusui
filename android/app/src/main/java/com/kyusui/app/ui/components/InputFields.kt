
package com.kyusui.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kyusui.app.ui.theme.KyusuiSpacing

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    isEnabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    trailingIconContentDescription: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            label = {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            },
            placeholder = {
                if (placeholder.isNotEmpty()) Text(text = placeholder)
            },
            isError = isError,
            enabled = isEnabled,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() }
            ),
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon?.let { vector ->
                {
                    Icon(
                        imageVector = vector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingIcon = trailingIcon?.let { vector ->
                {
                    IconButton(
                        onClick = { onTrailingIconClick?.invoke() },
                        enabled = isEnabled && onTrailingIconClick != null
                    ) {
                        Icon(
                            imageVector = vector,
                            contentDescription = trailingIconContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        FieldErrorMessage(errorMessage)
    }
}

@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    isEnabled: Boolean = true,
    isInitiallyVisible: Boolean = false,
    onVisibilityToggle: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    var isVisible by remember {
        mutableStateOf(isInitiallyVisible)
    }

    InputField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        isError = isError,
        errorMessage = errorMessage,
        isEnabled = isEnabled,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
        trailingIconContentDescription = if (isVisible) "Sembunyikan kata sandi" else "Tampilkan kata sandi",
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = if (isVisible) {
            Icons.Outlined.VisibilityOff
        } else {
            Icons.Outlined.Visibility
        },
        onTrailingIconClick = {
            isVisible = !isVisible
            onVisibilityToggle?.invoke()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih",
    isError: Boolean = false,
    errorMessage: String? = null,
    isEnabled: Boolean = true
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedOption ?: placeholder,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            label = {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            },
            readOnly = true,
            enabled = isEnabled,
            isError = isError,
            trailingIcon = {
                IconButton(
                    onClick = { expanded = !expanded },
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) {
                            "Tutup pilihan"
                        } else {
                            "Buka pilihan"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        DropdownMenu(
            expanded = expanded && isEnabled,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    enabled = isEnabled
                )
            }
        }

        FieldErrorMessage(errorMessage)
    }
}

@Composable
private fun FieldErrorMessage(errorMessage: String?) {
    if (errorMessage == null) return

    Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = KyusuiSpacing.Xs, start = KyusuiSpacing.Lg - KyusuiSpacing.Xs)
    )
}
