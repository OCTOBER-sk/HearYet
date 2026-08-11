package com.hearyet.app.core.ui.color

import androidx.compose.ui.graphics.Color

object HearYetColors {
    // ---- Base surface roles (dark-only, seeded from #D4856A) ----
    val Background = Color(0xFF000000)
    val OnBackground = Color(0xFFEDE0DA)

    val Surface = Color(0xFF000000)
    val OnSurface = Color(0xFFEDE0DA)

    val SurfaceVariant = Color(0xFF2A2A2A)
    val OnSurfaceVariant = Color(0xFFD8C2BB)

    val SurfaceContainerLowest = Color(0xFF0A0A0A)
    val SurfaceContainerLow = Color(0xFF141414)
    val SurfaceContainer = Color(0xFF1E1E1E)
    val SurfaceContainerHigh = Color(0xFF282828)
    val SurfaceContainerHighest = Color(0xFF323232)

    val Outline = Color(0xFFA18D87)
    val OutlineVariant = Color(0xFF53433E)

    // ---- Primary family ----
    val Primary = Color(0xFFDFC2B8)
    val OnPrimary = Color(0xFF442318)
    val PrimaryContainer = Color(0xFF5D3727)
    val OnPrimaryContainer = Color(0xFFFBDDD4)

    // ---- Secondary family ----
    val Secondary = Color(0xFFE2D0B5)
    val OnSecondary = Color(0xFF3E2D16)
    val SecondaryContainer = Color(0xFF574426)
    val OnSecondaryContainer = Color(0xFFFFDDB0)

    // ---- Tertiary family ----
    val Tertiary = Color(0xFFBBBBDC)
    val OnTertiary = Color(0xFF252546)
    val TertiaryContainer = Color(0xFF3C3C5E)
    val OnTertiaryContainer = Color(0xFFD9D8FA)

    // ---- Error family ----
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)

    // ---- Inverse / scrim ----
    val InverseSurface = Color(0xFFEDE0DA)
    val InverseOnSurface = Color(0xFF362F2C)
    val InversePrimary = Color(0xFF7A4E3E)
    val Scrim = Color(0xFF000000)

    // ---- Legacy aliases (keep every existing call site on the new palette) ----
    val Accent = Primary
    val AccentContainer = PrimaryContainer
    val SurfaceRaised = SurfaceContainer
    val SurfaceOutline = OutlineVariant
    val OnSurfaceMuted = OnSurfaceVariant
    val OnSurfaceDisabled = Color(0xFF6B6156)

    // ---- Sync status (functional, not part of the palette) ----
    val SyncGood = Color(0xFF6FBF73)
    val SyncDegraded = Color(0xFFD9A441)
    val SyncPoor = Color(0xFFD9584A)

    // ---- Special tokens (fixed, never affected by dynamic color) ----
    val PlayerAccent = Color(0xFFDFC2B8)
    val VideoPlayerBackground = Color(0xFF000000)
    val AlwaysWhite = Color(0xFFFFFFFF)
    val LogoColor = Color(0xFFE57357)

    // ---- AMOLED black overrides ----
    val AmoledBlack = Color(0xFF000000)
    val AmoledSurfaceContainer = Color(0xFF000000)
    val AmoledSurfaceContainerHigh = Color(0xFF0A0A0A)
    val AmoledSurfaceContainerHighest = Color(0xFF141414)
}
