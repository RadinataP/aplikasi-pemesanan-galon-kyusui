package com.kyusui.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeepBlue = Color(0xFF087FA8)
private val Aqua = Color(0xFF62D4C7)

@Composable
fun AuthBrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showBrandEyebrow: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 26.dp else 32.dp))
            .background(Brush.linearGradient(listOf(DeepBlue, Color(0xFF20AFC0), Aqua)))
            .padding(if (compact) 20.dp else 24.dp)
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp).size(if (compact) 76.dp else 116.dp)
            .background(Color.White.copy(alpha = .07f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 2.dp).size(36.dp)
            .background(Color.White.copy(alpha = .06f), CircleShape))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .14f)) {
                Icon(Icons.Filled.WaterDrop, contentDescription = "Berkah Water", tint = Color.White,
                    modifier = Modifier.padding(if (compact) 13.dp else 16.dp).size(if (compact) 28.dp else 34.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (showBrandEyebrow || !compact) {
                    Text("BERKAH WATER", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC9F5F6), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Text(title, style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .86f))
            }
        }
    }
}

@Composable
fun AuthFooterText(
    prompt: String,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.TextButton(onClick = onAction, enabled = enabled) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AuthSectionIntro(eyebrow: String, title: String, description: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
