package com.hearyet.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 shape slots, tuned to the HearYet corner-radius standards:
// small = thumbnails (8dp), medium = cards (16dp), large = artwork (20dp),
// extraLarge = bottom sheets (28dp top corners only).
val HearYetShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

// Named corner-radius standards for direct use in components.
object HearYetShapeTokens {
    val Card = RoundedCornerShape(16.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Button = RoundedCornerShape(100.dp)
    val Thumbnail = RoundedCornerShape(8.dp)
    val AudioArtwork = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(999.dp)
    val SubtitleLine = RoundedCornerShape(4.dp)
    val SessionQR = RoundedCornerShape(16.dp)
}

// Full-bleed edge cases (camera preview container, notification art)
val HearYetNoneShape = RoundedCornerShape(0.dp)
// Pill shapes — guest-count badge, avatar stack container
val HearYetPillShape = RoundedCornerShape(50)
