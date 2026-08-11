package com.hearyet.app.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hearyet.app.core.ui.designsystem.NextIcons

/**
 * FE §8 / §9.7 — the restyled Settings row primitive.
 *
 * Replaces NextPlayer's `ClickablePreferenceItem`/`NextSegmentedListItem` rows
 * on every Settings screen. Uses Section 4 tokens (warm `surfaceContainer`
 * backing, `Accent`-tinted icon) and keeps the TV focus affordance the old
 * segmented row had (primary border + slight scale while focused).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    ListItem(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .zIndex(if (isFocused) 1f else 0f)
            .scale(if (isFocused) 1.01f else 1f)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    )
                } else {
                    Modifier
                },
            ),
        enabled = enabled,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = description?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        interactionSource = interactionSource,
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun SettingsRowPreview() {
    SettingsRow(
        title = "Appearance",
        description = "Theme, colors, and fonts",
        icon = NextIcons.Appearance,
    )
}
