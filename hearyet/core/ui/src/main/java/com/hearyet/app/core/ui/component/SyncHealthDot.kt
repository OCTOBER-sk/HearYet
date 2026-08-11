package com.hearyet.app.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.model.SyncHealth

@Composable
fun SyncHealthDot(
    health: SyncHealth,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberMotionPreferences().reduceMotion,
) {
    val targetColor = when (health) {
        SyncHealth.GOOD -> HearYetColors.SyncGood
        SyncHealth.DEGRADED -> HearYetColors.SyncDegraded
        SyncHealth.POOR -> HearYetColors.SyncPoor
    }
    val dotColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 400),
        label = "syncHealthColor",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor),
        )
        Text(
            text = health.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val SyncHealth.label: String
    get() = when (this) {
        SyncHealth.GOOD -> "Good"
        SyncHealth.DEGRADED -> "Degraded"
        SyncHealth.POOR -> "Poor"
    }

@Preview
@Composable
private fun SyncHealthDotPreview() {
    SyncHealthDot(
        health = if (LocalInspectionMode.current) SyncHealth.GOOD else SyncHealth.DEGRADED,
        reduceMotion = true,
    )
}
