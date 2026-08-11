package com.hearyet.app.core.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.safeContentPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing)

fun Modifier.fullBleed(): Modifier = this
