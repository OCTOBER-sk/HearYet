package com.hearyet.app.feature.session.create

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.QrFrame
import com.hearyet.app.core.ui.component.HearYetSnackbarHost
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.rememberMotionPreferences
import com.hearyet.app.core.ui.component.sessionErrorMessage
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.core.ui.theme.SessionCodeStyle
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * Create Session bottom sheet — FE §9.4.
 *
 * Shown over Home when the user taps "Create."  The QR is generated immediately
 * (before media is picked) so guests can join while the Host browses files.
 *
 * §18 Addendum — AnimatedVisibility on QrFrame, animateIntAsState on guest count,
 * long-press-to-copy + Snackbar, share code/QR icon buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionSheet(
    qrBitmap: ImageBitmap?,
    sessionCode: String,
    sessionState: SessionState,
    guestCount: Int,
    hasMediaPicked: Boolean,
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onPlayFromUrl: () -> Unit,
    onStartPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HearYetColors.SurfaceRaised,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        CreateSessionContent(
            qrBitmap = qrBitmap,
            sessionCode = sessionCode,
            sessionState = sessionState,
            guestCount = guestCount,
            hasMediaPicked = hasMediaPicked,
            onPickMedia = onPickMedia,
            onPlayFromUrl = onPlayFromUrl,
            onStartPlayback = onStartPlayback,
        )
    }
}

/**
 * The sheet's internal content, extracted so it can be previewed independently
 * of the [ModalBottomSheet] scaffold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateSessionContent(
    qrBitmap: ImageBitmap?,
    sessionCode: String,
    sessionState: SessionState,
    guestCount: Int,
    hasMediaPicked: Boolean,
    onPickMedia: () -> Unit,
    onPlayFromUrl: () -> Unit,
    onStartPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // §4.5 — every animated element branches on reduced-motion.
    val reduceMotion = rememberMotionPreferences().reduceMotion

    // §18 — Animate guest count
    val animatedGuestCount by animateIntAsState(
        targetValue = guestCount,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 300),
        label = "guestCount",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ────────────────────────────────────────────────
        Text(
            text = "Create a session.",
            style = MaterialTheme.typography.headlineMedium,
            color = HearYetColors.OnBackground,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        // ── QR code (animated appearance) ─────────────────────────
        AnimatedVisibility(
            visible = qrBitmap != null,
            enter = if (reduceMotion) {
                fadeIn(snap())
            } else {
                fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = tween(300))
            },
            exit = if (reduceMotion) {
                fadeOut(snap())
            } else {
                fadeOut(tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200))
            },
        ) {
            QrFrame(
                bitmap = qrBitmap,
                modifier = Modifier,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // ── Session code (JetBrains Mono chip, selectable, long-press to copy) ─
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = HearYetColors.SurfaceContainerLow,
        ) {
            SelectionContainer {
                Text(
                    text = sessionCode,
                    style = SessionCodeStyle,
                    color = HearYetColors.OnBackground,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Session code", sessionCode))
                                scope.launch { snackbarHostState.showSnackbar("Code copied") }
                            },
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // ── Share QR action ─────────────────────────────────────────
        // Exports the QR bitmap as a PNG and hands it to the system share sheet.
        val bitmap = qrBitmap
        IconButton(
            onClick = {
                bitmap?.let { bmp -> shareQrBitmap(context, bmp) }
            },
            enabled = bitmap != null,
        ) {
            Icon(
                imageVector = NextIcons.Share,
                contentDescription = "Share QR",
                modifier = Modifier.size(22.dp),
                tint = if (bitmap != null) HearYetColors.Accent
                else HearYetColors.OnSurfaceDisabled,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // ── Guest-count line (animated) ────────────────────────────
        GuestCountLine(sessionState = sessionState, guestCount = animatedGuestCount)

        // ── Host-side error banner (BE §3/§17.7) ───────────────────
        // A failed advertising/discovery stack must never be silent: the host
        // was showing a QR that no guest could ever join. Surface the error
        // (with the underlying Nearby/Play-services status) right here.
        (sessionState as? SessionState.Error)?.let { err ->
            Spacer(modifier = Modifier.height(Spacing.md))
            HostErrorCard(
                message = sessionErrorMessage(err.reason),
                detail = err.detail,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // ── Media actions ─────────────────────────────────────────
        // §9.4 — Primary: pick local file (reuse existing picker)
        OutlinedButton(
            onClick = onPickMedia,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = HearYetColors.Accent,
            ),
        ) {
            Icon(
                imageVector = NextIcons.Player,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(
                text = if (hasMediaPicked) "Change media" else "Pick a file",
                color = HearYetColors.Accent,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Secondary: "Play from URL" per BE §12 triage table
        TextButton(onClick = onPlayFromUrl) {
            Text(
                text = "Play from URL",
                color = HearYetColors.OnSurfaceMuted,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // ── Start playback CTA ────────────────────────────────────
        Button(
            onClick = onStartPlayback,
            enabled = hasMediaPicked,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = HearYetColors.Accent,
                contentColor = HearYetColors.OnPrimary,
                disabledContainerColor = HearYetColors.SurfaceOutline,
                disabledContentColor = HearYetColors.OnSurfaceDisabled,
            ),
        ) {
            Text(
                text = "Start playback",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // ── Snackbar host for non-fatal confirmations ─────────────
        HearYetSnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun GuestCountLine(sessionState: SessionState, guestCount: Int) {    val isActive = sessionState == SessionState.Advertising ||
        sessionState == SessionState.WaitingForMedia

    val text = when {
        !isActive && guestCount == 0 -> ""
        guestCount == 0 -> "Waiting for guests to join…"
        guestCount == 1 -> "1 guest connected"
        else -> "$guestCount guests connected"
    }

    if (text.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (guestCount > 0) {
                Icon(
                    imageVector = NextIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = HearYetColors.Accent,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (guestCount > 0) HearYetColors.OnBackground
                else HearYetColors.OnSurfaceMuted,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

/**
 * Host-side error card — shown in the Create sheet when the session
 * coordinator reports an error (e.g. Nearby failed to start advertising).
 * The QR stays visible for reference, but the "waiting for guests" fiction
 * is replaced by the real failure and its underlying status.
 */
@Composable
private fun HostErrorCard(
    message: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = HearYetColors.Error.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(Spacing.md),
    ) {
        Column {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnError,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = HearYetColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Preview
@Composable
private fun CreateSessionContentWaitingPreview() {
    HearYetTheme {
        CreateSessionContent(
            qrBitmap = null,
            sessionCode = "A1B2C3",
            sessionState = SessionState.Advertising,
            guestCount = 0,
            hasMediaPicked = false,
            onPickMedia = {},
            onPlayFromUrl = {},
            onStartPlayback = {},
        )
    }
}

@Preview
@Composable
private fun CreateSessionContentGuestsPreview() {
    HearYetTheme {
        CreateSessionContent(
            qrBitmap = null,
            sessionCode = "X9Y7Z2",
            sessionState = SessionState.WaitingForMedia,
            guestCount = 2,
            hasMediaPicked = true,
            onPickMedia = {},
            onPlayFromUrl = {},
            onStartPlayback = {},
        )
    }
}

/**
 * §18 — Export the QR [ImageBitmap] to a cache PNG and launch the system share
 * sheet with it, via the app's FileProvider.
 */
private fun shareQrBitmap(context: Context, bitmap: ImageBitmap) {
    val file = File(context.cacheDir, "hearyet_qr_share.png")
    try {
        FileOutputStream(file).use { stream ->
            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    } catch (_: Exception) {
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share QR"))
}
