package com.kyusui.app.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyusui.app.R
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.SecondaryButton

private val BrandNavy = Color(0xFF153B50)
private val BrandBlue = Color(0xFF087FA5)
private val BrandTeal = Color(0xFF16B8C0)
private val BrandAqua = Color(0xFFDDF5F8)
private val BrandMuted = Color(0xFF6B8792)

/**
 * Brand-led session restoration screen.
 * Authentication state and navigation remain owned by AuthViewModel.
 */
@Composable
fun SplashScreen(
    viewModel: AuthViewModel,
    onEntryPointResolved: (AuthEntryPoint) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val entryPoint by viewModel.entryPoint.collectAsStateWithLifecycle()

    LaunchedEffect(entryPoint) {
        entryPoint?.let(onEntryPointResolved)
    }

    when (val state = uiState) {
        is AuthUiState.RestoringSession -> SplashLoading()
        is AuthUiState.RestoreFailed -> SplashRestoreFailed(
            message = state.message,
            onRetry = viewModel::restoreSession,
            onContinueToLogin = {
                viewModel.clearError()
                onEntryPointResolved(AuthEntryPoint.LOGIN)
            }
        )
        else -> SplashLoading()
    }
}

@Composable
private fun SplashLoading() {
    val motion = rememberInfiniteTransition(label = "berkah_water_splash")
    val dropOffset by motion.animateFloat(
        initialValue = -5f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drop_offset"
    )
    val rippleProgress by motion.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_progress"
    )
    val logoScale by motion.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF9FDFE), Color(0xFFF2FAFB), Color.White)
                )
            )
    ) {
        // Decorative flat wave bands, deliberately subtle and low contrast.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
                .align(Alignment.BottomCenter)
        ) {
            val h = size.height
            val w = size.width
            val backWave = Path().apply {
                moveTo(0f, h * 0.34f)
                cubicTo(w * 0.20f, h * 0.05f, w * 0.34f, h * 0.92f, w * 0.57f, h * 0.63f)
                cubicTo(w * 0.77f, h * 0.37f, w * 0.87f, h * 0.17f, w, h * 0.34f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            val frontWave = Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.22f, h * 0.40f, w * 0.35f, h * 0.96f, w * 0.58f, h * 0.79f)
                cubicTo(w * 0.78f, h * 0.64f, w * 0.89f, h * 0.43f, w, h * 0.59f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(backWave, Color(0xFFDDF5F8))
            drawPath(frontWave, Color(0xFF16B8C0))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.65f))

            Canvas(
                modifier = Modifier
                    .size(232.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height * 0.53f
                val unit = size.width / 232f

                // Three concentric ripple strokes; no bitmap or photorealistic effect.
                val rippleAlpha = (1f - rippleProgress) * 0.58f
                val rippleWidth = (76f + rippleProgress * 48f) * unit
                drawOval(
                    color = BrandTeal.copy(alpha = rippleAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - rippleWidth, cy + 31f * unit),
                    size = androidx.compose.ui.geometry.Size(rippleWidth * 2f, 22f * unit),
                    style = Stroke(width = 2.2f * unit)
                )
                drawOval(
                    color = BrandBlue.copy(alpha = rippleAlpha * 0.72f),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - rippleWidth * 0.72f, cy + 34f * unit),
                    size = androidx.compose.ui.geometry.Size(rippleWidth * 1.44f, 14f * unit),
                    style = Stroke(width = 1.5f * unit)
                )

                // Geometric droplet with a two-tone inset.
                val drop = Path().apply {
                    moveTo(cx, cy - 83f * unit + dropOffset * unit)
                    cubicTo(cx - 19f * unit, cy - 55f * unit + dropOffset * unit,
                        cx - 57f * unit, cy - 18f * unit + dropOffset * unit,
                        cx - 57f * unit, cy + 15f * unit + dropOffset * unit)
                    cubicTo(cx - 57f * unit, cy + 51f * unit + dropOffset * unit,
                        cx - 31f * unit, cy + 72f * unit + dropOffset * unit,
                        cx, cy + 72f * unit + dropOffset * unit)
                    cubicTo(cx + 31f * unit, cy + 72f * unit + dropOffset * unit,
                        cx + 57f * unit, cy + 51f * unit + dropOffset * unit,
                        cx + 57f * unit, cy + 15f * unit + dropOffset * unit)
                    cubicTo(cx + 57f * unit, cy - 18f * unit + dropOffset * unit,
                        cx + 19f * unit, cy - 55f * unit + dropOffset * unit,
                        cx, cy - 83f * unit + dropOffset * unit)
                    close()
                }
                drawPath(drop, BrandBlue)
                val inset = Path().apply {
                    moveTo(cx + 1f * unit, cy - 77f * unit + dropOffset * unit)
                    cubicTo(cx + 22f * unit, cy - 45f * unit + dropOffset * unit,
                        cx + 45f * unit, cy - 10f * unit + dropOffset * unit,
                        cx + 45f * unit, cy + 17f * unit + dropOffset * unit)
                    cubicTo(cx + 45f * unit, cy + 45f * unit + dropOffset * unit,
                        cx + 26f * unit, cy + 61f * unit + dropOffset * unit,
                        cx + 1f * unit, cy + 63f * unit + dropOffset * unit)
                    cubicTo(cx - 22f * unit, cy + 61f * unit + dropOffset * unit,
                        cx - 34f * unit, cy + 43f * unit + dropOffset * unit,
                        cx - 34f * unit, cy + 20f * unit + dropOffset * unit)
                    cubicTo(cx - 34f * unit, cy - 7f * unit + dropOffset * unit,
                        cx - 9f * unit, cy - 48f * unit + dropOffset * unit,
                        cx + 1f * unit, cy - 77f * unit + dropOffset * unit)
                    close()
                }
                drawPath(inset, Color(0xFF20B6D0))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "BERKAH WATER",
                color = BrandNavy,
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "Air Bersih, Layanan Terpercaya",
                color = BrandMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.75f))

            // A real indeterminate indicator: never implies a fake percentage.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFDCEFF1))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.34f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BrandTeal)
                )
            }
            Spacer(modifier = Modifier.height(17.dp))
            Text(
                text = "Menyiapkan layanan Anda",
                color = BrandNavy,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Mohon tunggu sebentar",
                color = BrandMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(0.42f))
            Text(
                text = "BERKAH WATER  •  LAYANAN AIR MINUM",
                color = Color(0xFF91AAB2),
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SplashRestoreFailed(
    message: String,
    onRetry: () -> Unit,
    onContinueToLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5FAFB))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFECEE),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "!",
                        color = Color(0xFFB93845),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Sesi belum dapat dipulihkan",
                style = MaterialTheme.typography.titleLarge,
                color = BrandNavy,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            InlineError(
                message = message,
                onRetry = onRetry,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            PrimaryButton(text = "Coba Lagi", onClick = onRetry, fillWidth = true)
            SecondaryButton(
                text = "Lanjut ke Login",
                onClick = onContinueToLogin,
                fillWidth = true,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
