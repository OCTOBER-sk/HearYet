package com.hearyet.app.core.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import com.hearyet.app.core.ui.color.HearYetColors

/**
 * Custom ripple configuration using the warm accent color (#DFC2B8)
 * for ripple effects instead of the default white.
 *
 * Applied globally via [androidx.compose.material3.LocalRippleConfiguration]
 * in [HearYetTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
val HearYetRippleConfiguration = RippleConfiguration(
    color = HearYetColors.PlayerAccent,
    rippleAlpha = RippleAlpha(
        pressedAlpha = 0.24f,
        focusedAlpha = 0.12f,
        draggedAlpha = 0.08f,
        hoveredAlpha = 0.08f,
    ),
)
