package com.hearyet.app.feature.session.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.safeContentPadding
import com.hearyet.app.core.ui.theme.HearYetTheme

/**
 * Name entry screen shown before the QR scanner — FE §9.5.
 *
 * Asks for a display name so the Host's guest list can show something
 * meaningful.  Pre-filled with the device model as a fallback default.
 */
@Composable
fun JoinNameEntryScreen(
    defaultName: String,
    onContinue: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(defaultName) }

    Scaffold(
        modifier = modifier,
        containerColor = HearYetColors.Background,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(modifier = Modifier.height(Spacing.xxl))

            Text(
                text = "Join a session",
                style = MaterialTheme.typography.headlineMedium,
                color = HearYetColors.OnBackground,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "Your name will be shown to the host.",
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnSurfaceMuted,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HearYetColors.OnBackground,
                    unfocusedTextColor = HearYetColors.OnBackground,
                    focusedBorderColor = HearYetColors.Accent,
                    unfocusedBorderColor = HearYetColors.SurfaceOutline,
                    focusedLabelColor = HearYetColors.Accent,
                    unfocusedLabelColor = HearYetColors.OnSurfaceMuted,
                    cursorColor = HearYetColors.Accent,
                ),
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = { onContinue(name.trim().ifBlank { defaultName }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HearYetColors.Accent,
                    contentColor = HearYetColors.OnPrimary,
                    disabledContainerColor = HearYetColors.SurfaceOutline,
                    disabledContentColor = HearYetColors.OnSurfaceDisabled,
                ),
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview
@Composable
private fun JoinNameEntryScreenPreview() {
    HearYetTheme {
        JoinNameEntryScreen(
            defaultName = "Pixel 8",
            onContinue = {},
        )
    }
}
