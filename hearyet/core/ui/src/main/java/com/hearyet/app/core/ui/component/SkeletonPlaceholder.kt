package com.hearyet.app.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.hearyet.app.core.ui.color.HearYetColors
import kotlinx.coroutines.delay

/**
 * Simple shimmer placeholder — FE Addendum §17.
 *
 * Used only for RecentActivityShelf's single-frame DataStore read gap.
 * Not a general loading system.
 */
@Composable
fun SkeletonPlaceholder(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    reduceMotion: Boolean = rememberMotionPreferences().reduceMotion,
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // §4.5 — reduced-motion: render a static block, never an animated pulse.
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            visible = !visible
            delay(600)
        }
    }

    val color = if (visible) HearYetColors.SurfaceOutline else HearYetColors.Surface

    Box(
        modifier = modifier
            .then(
                if (width != androidx.compose.ui.unit.Dp.Unspecified)
                    Modifier.width(width).height(height)
                else
                    Modifier.fillMaxWidth().height(height)
            )
            .background(color, MaterialTheme.shapes.medium),
    )
}
