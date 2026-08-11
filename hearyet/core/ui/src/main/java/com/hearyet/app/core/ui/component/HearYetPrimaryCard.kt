package com.hearyet.app.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.theme.HearYetShapes

@Composable
fun HearYetPrimaryCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    reduceMotion: Boolean = rememberMotionPreferences().reduceMotion,
    subtitle: String? = null, // §17 — optional subtitle line under icon+label
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = 0.55f,
            stiffness = 400f,
        ),
        label = "primaryCardPressScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = HearYetShapes.large,
        color = HearYetColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = HearYetColors.Accent,
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let { sub ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = HearYetColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun HearYetPrimaryCardPreview() {
    HearYetPrimaryCard(label = "Create", onClick = {}, reduceMotion = true)
}
