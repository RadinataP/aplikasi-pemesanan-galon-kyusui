package com.kyusui.app.ui.courier.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CourierStatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

@Composable
fun CourierCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    containerColor: Color = CourierColors.Surface,
    elevation: Dp = CourierDimensions.CardElevation,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(CourierDimensions.CardRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, CourierColors.Outline)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun CourierSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(CourierSpacing.Sm))
            trailingContent()
        }
    }
}

@Composable
fun CourierStatusBadge(
    text: String,
    tone: CourierStatusTone,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (tone) {
        CourierStatusTone.INFO -> CourierColors.InfoContainer to CourierColors.Info
        CourierStatusTone.SUCCESS -> CourierColors.SuccessContainer to CourierColors.Success
        CourierStatusTone.WARNING -> CourierColors.WarningContainer to CourierColors.Warning
        CourierStatusTone.ERROR -> CourierColors.ErrorContainer to CourierColors.Error
        CourierStatusTone.NEUTRAL -> CourierColors.SurfaceSoft to CourierColors.TextSecondary
    }

    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(CourierDimensions.BadgeRadius)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CourierPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CourierDimensions.ButtonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(CourierDimensions.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CourierColors.Primary,
            contentColor = CourierColors.White,
            disabledContainerColor = CourierColors.Outline,
            disabledContentColor = CourierColors.TextSecondary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = CourierColors.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(CourierSpacing.Sm))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(CourierSpacing.Sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CourierSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CourierDimensions.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(CourierDimensions.ControlRadius),
        border = BorderStroke(1.dp, CourierColors.Outline)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(CourierSpacing.Sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = CourierColors.Primary
        )
    }
}


/** Root navigation shown only on Courier's top-level destinations. */
enum class CourierRootTab { HOME, TASKS, PROFILE }

@Composable
fun CourierRootBottomBar(
    selected: CourierRootTab,
    onHome: () -> Unit,
    onTasks: () -> Unit,
    onProfile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CourierColors.Surface,
        contentColor = CourierColors.TextPrimary,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CourierBottomBarItem(
                modifier = Modifier.weight(1f),
                selected = selected == CourierRootTab.HOME,
                icon = Icons.Outlined.Home,
                label = "Beranda",
                onClick = onHome
            )
            CourierBottomBarItem(
                modifier = Modifier.weight(1f),
                selected = selected == CourierRootTab.TASKS,
                icon = Icons.Outlined.LocalShipping,
                label = "Tugas",
                onClick = onTasks
            )
            CourierBottomBarItem(
                modifier = Modifier.weight(1f),
                selected = selected == CourierRootTab.PROFILE,
                icon = Icons.Outlined.Person,
                label = "Profil",
                onClick = onProfile
            )
        }
    }
}

@Composable
private fun CourierBottomBarItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val foreground = if (selected) CourierColors.Primary else CourierColors.TextSecondary
    val background = if (selected) CourierColors.SurfaceBlue else Color.Transparent

    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = foreground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                maxLines = 1
            )
        }
    }
}


@Composable
fun CourierPageIntro(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = CourierColors.Primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = CourierColors.Navy
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = CourierColors.TextSecondary
        )
    }
}


/**
 * Compatibility page heading used by shared screens that are themed from a Courier route.
 * Keep this API small and delegate to the same Courier visual tokens.
 */
@Composable
fun CourierPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = CourierColors.Primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = CourierColors.Navy
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = CourierColors.TextSecondary
        )
    }
}
