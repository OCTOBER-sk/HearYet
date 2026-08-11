package com.hearyet.app.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.hearyet.app.core.ui.color.HearYetColors
import java.util.Calendar

/**
 * Time-of-day greeting replacing the static "HearYet" title on Home — FE Addendum §17.
 *
 * Uses the same titleLarge/headlineMedium type role as the static text it replaces.
 */
@Composable
fun HomeGreeting(modifier: Modifier = Modifier) {
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning."
            in 12..16 -> "Good afternoon."
            in 17..20 -> "Good evening."
            else -> "Good night."
        }
    }

    Text(
        text = greetingText,
        style = MaterialTheme.typography.headlineMedium,
        color = HearYetColors.OnBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
