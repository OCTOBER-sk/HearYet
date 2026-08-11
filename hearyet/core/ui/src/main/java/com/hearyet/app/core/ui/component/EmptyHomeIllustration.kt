package com.hearyet.app.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors

/**
 * Empty state for the Home Recent Activity shelf — FE Addendum §17.
 *
 * Uses the same concentric-arc line-art language as the app icon (Section 19).
 * Shown only during the narrow first-launch-ever window before any activity data exists.
 */
@Composable
fun EmptyHomeIllustration(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Concentric arcs — same visual language as app icon
        Canvas(modifier = Modifier.size(80.dp)) {
            val accent = HearYetColors.Accent
            val stroke = 2.5.dp.toPx()
            val cx = size.width / 2
            val cy = size.height / 2

            // Outer arc (open ring)
            drawArc(
                color = accent,
                startAngle = -30f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(cx - 36.dp.toPx(), cy - 36.dp.toPx()),
                size = Size(72.dp.toPx(), 72.dp.toPx()),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Inner arc
            drawArc(
                color = accent,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(cx - 24.dp.toPx(), cy - 24.dp.toPx()),
                size = Size(48.dp.toPx(), 48.dp.toPx()),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = "No recent activity",
            style = MaterialTheme.typography.labelLarge,
            color = HearYetColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Watch something or start a session to see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = HearYetColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
