package com.hearyet.app.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scales the element to [targetScale] when pressed.
 * Respects reduced motion by skipping the animation.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberMotionPreferences().reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = if (reduceMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.4f, stiffness = 400f)
        },
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Animates elevation from [restElevation] to [pressedElevation] on press.
 */
fun Modifier.animatedElevation(
    interactionSource: MutableInteractionSource,
    restElevation: Dp = 0.dp,
    pressedElevation: Dp = 2.dp,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberMotionPreferences().reduceMotion
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) pressedElevation.value else restElevation.value,
        animationSpec = if (reduceMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.6f, stiffness = 300f)
        },
        label = "animatedElevation",
    )
    graphicsLayer {
        shadowElevation = elevation
    }
}
