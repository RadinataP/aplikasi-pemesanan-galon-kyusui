package com.kyusui.app.ui.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Floating, light customer navigation with a clear active destination. */
@Composable
fun CustomerBottomBar(
    selected: CustomerTab,
    onHome: () -> Unit,
    onProducts: () -> Unit,
    onOrders: () -> Unit,
    onAccount: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomerNavigationItem(selected == CustomerTab.HOME, "Beranda", onHome) {
                Icon(Icons.Filled.Home, contentDescription = null)
            }
            CustomerNavigationItem(selected == CustomerTab.PRODUCTS, "Produk", onProducts) {
                Icon(Icons.Outlined.WaterDrop, contentDescription = null)
            }
            CustomerNavigationItem(selected == CustomerTab.ORDERS, "Pesanan", onOrders) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
            }
            CustomerNavigationItem(selected == CustomerTab.ACCOUNT, "Akun", onAccount) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null)
            }
        }
    }
}

@Composable
private fun CustomerNavigationItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "customerNavScale"
    )
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "customerNavColor"
    )
    Column(
        modifier = Modifier.fillMaxWidth(0.25f).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.size(width = 54.dp, height = 34.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides color,
                    content = icon
                )
            }
        }
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

enum class CustomerTab { HOME, PRODUCTS, ORDERS, ACCOUNT }
