package com.hearyet.app.feature.session.join

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.hearyet.app.core.model.SessionError
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.OtpCodeEntry
import com.hearyet.app.core.ui.component.ScannerFrameOverlay
import com.hearyet.app.core.ui.component.SessionStateLadder
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.sessionErrorMessage
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.qr.QrScannerAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scanner screen modes.
 */
enum class JoinScreenMode {
    /** Camera active, scanning for QR. */
    Scanning,
    /** Manual code entry text field shown. */
    CodeEntry,
    /** QR decoded — showing state ladder. */
    Connecting,
    /** Error occurred — show message + retry. */
    Error,
}

/**
 * Join Session scanner + code-entry screen — FE §9.5.
 *
 * Full-screen CameraX preview with [ScannerFrameOverlay], "Enter code instead"
 * fallback, and [SessionStateLadder] bound to real [SessionState] transitions.
 */
@Composable
fun JoinSessionScreen(
    sessionState: SessionState,
    screenMode: JoinScreenMode,
    errorReason: SessionError?,
    errorDetail: String? = null,
    onQrDecoded: (rawPayload: String) -> Unit,
    onCodeEntered: (code: String) -> Unit,
    onSwitchToCodeEntry: () -> Unit,
    onSwitchToScanning: () -> Unit,
    onRetry: () -> Unit,
    onBackToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    when (screenMode) {
        JoinScreenMode.Scanning -> {
            ScannerView(
                onQrDecoded = onQrDecoded,
                onSwitchToCodeEntry = onSwitchToCodeEntry,
                modifier = modifier,
            )
        }

        JoinScreenMode.CodeEntry -> {
            CodeEntryView(
                onCodeEntered = onCodeEntered,
                onSwitchToScanning = onSwitchToScanning,
                modifier = modifier,
            )
        }

        JoinScreenMode.Connecting -> {
            ConnectingView(
                sessionState = sessionState,
                modifier = modifier,
            )
        }

        JoinScreenMode.Error -> {
            ErrorView(
                errorReason = errorReason,
                errorDetail = errorDetail,
                onRetry = onRetry,
                onBackToHome = onBackToHome,
                modifier = modifier,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Scanner view (camera + frame overlay)
// ═══════════════════════════════════════════════════════════════════════

/**
 * M-6 — ownership of the [ScannerView] CameraX bind + analyzer executor.
 * All accesses happen on the main thread (the ProcessCameraProvider listener
 * runs on the main executor, and [DisposableEffect] runs on dispose). A
 * [released] guard stops a late-resolving provider from binding the camera
 * after the scanner already left composition.
 */
private class ScannerResources {
    var cameraProvider: ProcessCameraProvider? = null
    var analyzerExecutor: ExecutorService? = null
    var released: Boolean = false

    /** Unbind the camera and shut down the analyzer executor exactly once. */
    fun release() {
        if (released) return
        released = true
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzerExecutor?.shutdown()
        analyzerExecutor = null
    }
}

@Composable
private fun ScannerView(
    onQrDecoded: (String) -> Unit,
    onSwitchToCodeEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // M-6 — own the CameraX bind + analyzer executor so leaving this composable
    // tears them down. Previously each scanner open leaked a single-thread
    // executor and kept the camera hot, and a stale analyzer kept firing
    // onQrDecoded while the user had switched to code entry / an error state.
    val scannerResources = remember { ScannerResources() }

    DisposableEffect(lifecycleOwner) {
        onDispose { scannerResources.release() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera preview — full-bleed behind system bars (FE §5 fullBleed exception)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        // The scanner may already have left composition (e.g. the user
                        // switched to code entry) before the provider resolved.
                        if (scannerResources.released) return@addListener
                        val cameraProvider = cameraProviderFuture.get()
                        scannerResources.cameraProvider = cameraProvider
                        val preview = CameraPreview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzerExecutor = Executors.newSingleThreadExecutor()
                        scannerResources.analyzerExecutor = analyzerExecutor
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    analyzerExecutor,
                                    QrScannerAnalyzer { raw ->
                                        Log.d("JoinSessionScreen", "QR decoded")
                                        onQrDecoded(raw)
                                    },
                                )
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis,
                            )
                        } catch (e: Exception) {
                            Log.e("JoinSessionScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Dim overlay for contrast with frame + text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HearYetColors.Background.copy(alpha = 0.35f)),
        )

        // Centered content: frame + instruction + code-entry link
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Scanner framing guide (static corner brackets, slow pulse)
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScannerFrameOverlay(modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "Point your camera at the host's QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnBackground,
            )

            Spacer(modifier = Modifier.weight(1f))

            // "Enter code instead" link
            TextButton(onClick = onSwitchToCodeEntry) {
                Text(
                    text = "Enter code instead",
                    color = HearYetColors.Accent,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Code entry view (manual 6-char input)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CodeEntryView(
    onCodeEntered: (String) -> Unit,
    onSwitchToScanning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Enter session code",
                style = MaterialTheme.typography.headlineMedium,
                color = HearYetColors.OnBackground,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "The 6-character code shown below the host's QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnSurfaceMuted,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // §18 — OTP-style 6-box code entry
            OtpCodeEntry(
                code = code,
                onCodeChanged = { code = it },
                onSubmit = onSubmit@{
                    if (code.length == 6) onCodeEntered(code)
                },
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = { if (code.length == 6) onCodeEntered(code) },
                enabled = code.length == 6,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HearYetColors.Accent,
                    contentColor = HearYetColors.OnPrimary,
                    disabledContainerColor = HearYetColors.SurfaceOutline,
                    disabledContentColor = HearYetColors.OnSurfaceDisabled,
                ),
            ) {
                Text(
                    text = "Join",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            TextButton(onClick = onSwitchToScanning) {
                Text(
                    text = "Scan QR instead",
                    color = HearYetColors.OnSurfaceMuted,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Connecting view (state ladder)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ConnectingView(
    sessionState: SessionState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SessionStateLadder(currentState = sessionState)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Error view
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorView(
    errorReason: SessionError?,
    errorDetail: String?,
    onRetry: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // FE §9.11 — non-retryable errors: only "Back to Home"
    val canRetry = when (errorReason) {
        SessionError.PAYLOAD_INVALID, SessionError.DEVICE_INCOMPATIBLE,
        SessionError.HOST_UNREACHABLE -> false
        else -> true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = sessionErrorMessage(errorReason),
                style = MaterialTheme.typography.bodyLarge,
                color = HearYetColors.OnBackground,
                textAlign = TextAlign.Center,
            )

            // Underlying technical cause (e.g. the exact Nearby/Play-services
            // status) — never guess why a generic error fired.
            if (!errorDetail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = errorDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HearYetColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }

            // FE §9.11 — DISCOVERY_FAILED carries its supporting suggestion.
            if (errorReason == SessionError.DISCOVERY_FAILED) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Make sure the host is still on the Join screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HearYetColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Recovery action per FE §9.11 table
            if (canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HearYetColors.Accent,
                        contentColor = HearYetColors.OnPrimary,
                    ),
                ) {
                    Text(when (errorReason) {
                        SessionError.PERMISSION_MISSING -> "Grant access"
                        else -> "Try again"
                    })
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Always a way back to Home — no error state is a dead end (FE §9.11)
            TextButton(
                onClick = onBackToHome,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Back to Home",
                    color = HearYetColors.OnSurfaceMuted,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

@Preview
@Composable
private fun JoinSessionConnectingPreview() {
    HearYetTheme {
        JoinSessionScreen(
            sessionState = SessionState.ClockSyncing,
            screenMode = JoinScreenMode.Connecting,
            errorReason = null,
            onQrDecoded = {},
            onCodeEntered = {},
            onSwitchToCodeEntry = {},
            onSwitchToScanning = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun JoinSessionErrorPreview() {
    HearYetTheme {
        JoinSessionScreen(
            sessionState = SessionState.Error(SessionError.QR_INVALID),
            screenMode = JoinScreenMode.Error,
            errorReason = SessionError.QR_INVALID,
            onQrDecoded = {},
            onCodeEntered = {},
            onSwitchToCodeEntry = {},
            onSwitchToScanning = {},
            onRetry = {},
        )
    }
}
