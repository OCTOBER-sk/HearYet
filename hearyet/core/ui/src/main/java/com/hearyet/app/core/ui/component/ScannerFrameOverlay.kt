package com.hearyet.app.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import kotlinx.coroutines.delay

@Composable
fun ScannerFrameOverlay(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberMotionPreferences().reduceMotion,
) {
    var pulseHigh by remember(reduceMotion) { mutableStateOf(false) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            pulseHigh = false
            return@LaunchedEffect
        }
        while (true) {
            pulseHigh = !pulseHigh
            delay(1200)
        }
    }

    val opacity by animateFloatAsState(
        targetValue = if (reduceMotion || pulseHigh) 1f else 0.40f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 900),
        label = "scannerFrameOpacity",
    )

    val frameColor = HearYetColors.Accent

    Canvas(modifier = modifier) {
        val cornerLength = 28.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val color = frameColor.copy(alpha = opacity)
        val right = size.width
        val bottom = size.height

        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(right, 0f), end = androidx.compose.ui.geometry.Offset(right - cornerLength, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(right, 0f), end = androidx.compose.ui.geometry.Offset(right, cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, bottom), end = androidx.compose.ui.geometry.Offset(cornerLength, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, bottom), end = androidx.compose.ui.geometry.Offset(0f, bottom - cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(right, bottom), end = androidx.compose.ui.geometry.Offset(right - cornerLength, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Square)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(right, bottom), end = androidx.compose.ui.geometry.Offset(right, bottom - cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Square)
    }
}

@Preview
@Composable
private fun ScannerFrameOverlayPreview() {
    ScannerFrameOverlay(modifier = Modifier, reduceMotion = true)
}
