package com.hearyet.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.hearyet.app.core.ui.R

// Font families loaded via Google Fonts provider (requires GMS).
// On devices without Google Play Services, system fallbacks are used.
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val OutfitFamily = FontFamily(
    Font(googleFont = GoogleFont("Outfit"), fontProvider = fontProvider),
)
private val InterFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider),
)
val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = fontProvider),
)

// Full type scale, all 14 tokens.
val HearYetTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Scale every TextStyle in the typography by [userScale]. The result is a
 * fresh Typography with all 14 type tokens proportionally resized.
 */
fun Typography.scaleForUser(userScale: Float): Typography {
    if (userScale == 1.0f) return this
    return Typography(
        displayLarge = displayLarge.copy(
            fontSize = displayLarge.fontSize * userScale,
            lineHeight = displayLarge.lineHeight * userScale,
            letterSpacing = displayLarge.letterSpacing * userScale,
        ),
        displayMedium = displayMedium.copy(
            fontSize = displayMedium.fontSize * userScale,
            lineHeight = displayMedium.lineHeight * userScale,
            letterSpacing = displayMedium.letterSpacing * userScale,
        ),
        displaySmall = displaySmall.copy(
            fontSize = displaySmall.fontSize * userScale,
            lineHeight = displaySmall.lineHeight * userScale,
            letterSpacing = displaySmall.letterSpacing * userScale,
        ),
        headlineLarge = headlineLarge.copy(
            fontSize = headlineLarge.fontSize * userScale,
            lineHeight = headlineLarge.lineHeight * userScale,
            letterSpacing = headlineLarge.letterSpacing * userScale,
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = headlineMedium.fontSize * userScale,
            lineHeight = headlineMedium.lineHeight * userScale,
            letterSpacing = headlineMedium.letterSpacing * userScale,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = headlineSmall.fontSize * userScale,
            lineHeight = headlineSmall.lineHeight * userScale,
            letterSpacing = headlineSmall.letterSpacing * userScale,
        ),
        titleLarge = titleLarge.copy(
            fontSize = titleLarge.fontSize * userScale,
            lineHeight = titleLarge.lineHeight * userScale,
            letterSpacing = titleLarge.letterSpacing * userScale,
        ),
        titleMedium = titleMedium.copy(
            fontSize = titleMedium.fontSize * userScale,
            lineHeight = titleMedium.lineHeight * userScale,
            letterSpacing = titleMedium.letterSpacing * userScale,
        ),
        titleSmall = titleSmall.copy(
            fontSize = titleSmall.fontSize * userScale,
            lineHeight = titleSmall.lineHeight * userScale,
            letterSpacing = titleSmall.letterSpacing * userScale,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = bodyLarge.fontSize * userScale,
            lineHeight = bodyLarge.lineHeight * userScale,
            letterSpacing = bodyLarge.letterSpacing * userScale,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = bodyMedium.fontSize * userScale,
            lineHeight = bodyMedium.lineHeight * userScale,
            letterSpacing = bodyMedium.letterSpacing * userScale,
        ),
        bodySmall = bodySmall.copy(
            fontSize = bodySmall.fontSize * userScale,
            lineHeight = bodySmall.lineHeight * userScale,
            letterSpacing = bodySmall.letterSpacing * userScale,
        ),
        labelLarge = labelLarge.copy(
            fontSize = labelLarge.fontSize * userScale,
            lineHeight = labelLarge.lineHeight * userScale,
            letterSpacing = labelLarge.letterSpacing * userScale,
        ),
        labelMedium = labelMedium.copy(
            fontSize = labelMedium.fontSize * userScale,
            lineHeight = labelMedium.lineHeight * userScale,
            letterSpacing = labelMedium.letterSpacing * userScale,
        ),
        labelSmall = labelSmall.copy(
            fontSize = labelSmall.fontSize * userScale,
            lineHeight = labelSmall.lineHeight * userScale,
            letterSpacing = labelSmall.letterSpacing * userScale,
        ),
    )
}

/**
 * Monospace style for displaying session join codes (6-character alphanumeric
 * identifiers). Uses [JetBrainsMonoFamily] for unambiguous glyph recognition.
 *
 * Sits outside the 14-token M3 type scale because it is a domain-specific
 * utility, not a typographic role.
 */
val SessionCodeStyle: TextStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.15.sp,
)

/**
 * Apply a user-tunable font scale to a custom [TextStyle] that lives outside
 * the 14-token M3 typography (e.g. [SessionCodeStyle]).
 */
fun scaleCustomTextStyle(style: TextStyle, scale: Float): TextStyle {
    if (scale == 1.0f) return style
    return style.copy(
        fontSize = style.fontSize * scale,
        lineHeight = style.lineHeight * scale,
        letterSpacing = style.letterSpacing * scale,
    )
}
