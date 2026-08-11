package com.hearyet.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors

/**
 * 6-digit OTP-style code entry boxes — FE Addendum §18, Join scanner refinements.
 *
 * Each box holds one glyph. Uses the same sessionCode model and onCodeEntered path
 * as the original free-text field. Purely an input-affordance change.
 */
@Composable
fun OtpCodeEntry(
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    boxCount: Int = 6,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    // §18 — "Accent on focus": request focus on open so the keyboard appears
    // immediately and the active box is visibly highlighted while typing.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        for (i in 0 until boxCount) {
            val char = code.getOrElse(i) { ' ' }
            val isFilled = i < code.length
            val isActiveBox = focused && i == code.length.coerceAtMost(boxCount - 1)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = 1.5.dp,
                        color = when {
                            isFilled || isActiveBox -> HearYetColors.Accent
                            else -> HearYetColors.SurfaceOutline
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                    .background(
                        color = when {
                            isFilled || isActiveBox -> HearYetColors.AccentContainer
                            else -> HearYetColors.Surface
                        },
                        shape = MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (char == ' ') "" else char.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HearYetColors.OnBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    // Invisible text field to capture keyboard input
    BasicTextField(
        value = TextFieldValue(
            text = code,
            selection = TextRange(code.length),
        ),
        onValueChange = { newValue ->
            val filtered = newValue.text.uppercase().filter {
                it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
            }.take(boxCount)
            onCodeChanged(filtered)
            if (filtered.length == boxCount) {
                onSubmit()
            }
        },
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(
            onGo = { if (code.length == boxCount) onSubmit() },
        ),
        cursorBrush = SolidColor(HearYetColors.Accent),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HearYetColors.Background),
    )
}
