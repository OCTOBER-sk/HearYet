package com.hearyet.app.feature.permission

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.R
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.designsystem.NextIcons

/**
 * Permission that HearYet needs before invoking a specific feature.
 *
 * FE §9.10 — one shared composable, parameterized per permission.
 * Reached from Create / Join / Watch at the exact moment the feature is invoked,
 * per BE §1's contextual permission rule — never speculatively.
 */
enum class HearYetPermission(
    val explanationResId: Int,
) {
    CAMERA(R.string.permission_camera_explanation),
    NEARBY(R.string.permission_nearby_explanation),
    STORAGE(R.string.permission_storage_explanation),
}

/**
 * Runtime permissions required for Nearby/BT sessions, gated by platform version.
 * Requested only when Create/Join is tapped (BE §1) — never at app launch.
 */
fun nearbyRuntimePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    // BE §3/§17.7 — Nearby discovery needs location access on every Android
    // version (BLE scanning): the old API-30 cap made Nearby fail with status
    // 8034 (MISSING_PERMISSION_ACCESS_COARSE_LOCATION) on Android 11+.
    // FINE implies COARSE in the same permission group, so FINE suffices.
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

/**
 * Permission-required screen — FE §9.10.
 *
 * @param permission which permission is missing.
 * @param onGrantAccess "Grant access" → system prompt or Settings deep link.
 * @param onGoBack way back out that doesn't strand the user.
 */
@Composable
fun PermissionRequiredScreen(
    permission: HearYetPermission,
    onGrantAccess: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = NextIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = HearYetColors.Accent,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = stringResource(permission.explanationResId),
                style = MaterialTheme.typography.bodyLarge,
                color = HearYetColors.OnBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = onGrantAccess,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HearYetColors.Accent,
                    contentColor = HearYetColors.OnPrimary,
                ),
            ) {
                Text(stringResource(R.string.grant_access_action))
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            TextButton(
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.go_back),
                    color = HearYetColors.OnSurfaceMuted,
                )
            }
        }
    }
}
