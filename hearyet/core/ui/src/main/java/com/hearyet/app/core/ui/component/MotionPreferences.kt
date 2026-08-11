package com.hearyet.app.core.ui.component

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MotionPreferences(private val contentResolver: ContentResolver) {
    val reduceMotion: Boolean
        get() = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
}

@Composable
fun rememberMotionPreferences(): MotionPreferences {
    val contentResolver = LocalContext.current.contentResolver
    return remember(contentResolver) { MotionPreferences(contentResolver) }
}
