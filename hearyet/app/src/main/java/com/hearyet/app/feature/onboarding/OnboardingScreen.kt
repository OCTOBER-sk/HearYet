package com.hearyet.app.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.safeContentPadding

private data class OnboardingPage(
    val icon: ImageVector,
    val headline: String,
    val body: String,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Rounded.PlayCircle,
        headline = "HearYet plays what you love, and shares it nearby.",
        body = "A premium media player for watching, listening, and creating shared audio sessions.",
    ),
    OnboardingPage(
        icon = Icons.Rounded.GroupAdd,
        headline = "Host a session, or join one nearby.",
        body = "Start playback on your device to become the host, then share the QR code so guests can sync to your audio in real time.",
    ),
    OnboardingPage(
        icon = Icons.Rounded.Headphones,
        headline = "Know your way around.",
        body = "Home gets you back to active sessions, Library is where you browse media, and the mini-player expands into full controls whenever something is playing.",
    ),
)

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    onLearnMore: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingViewModel.PAGE_COUNT })
    val currentPage by viewModel.currentPage.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.finished.collect { onNavigateToHome() }
    }

    var granted by remember { mutableStateOf(isPermissionGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(granted) {
        if (granted) {
            viewModel.onPermissionGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                if (page == OnboardingViewModel.LAST_PAGE) {
                    if (scanState == ScanState.Scanning) {
                        ScanningPage(progress = scanProgress)
                    } else {
                        OnboardingPageContent(
                            page = onboardingPages[page],
                            primaryButtonLabel = "Get started",
                            onPrimaryClick = viewModel::onGetStarted,
                            showSecondaryButton = true,
                            secondaryButtonLabel = "Learn more",
                            onSecondaryClick = onLearnMore,
                        )
                    }
                } else {
                    OnboardingPageContent(
                        page = onboardingPages[page],
                        primaryButtonLabel = "Continue",
                        onPrimaryClick = viewModel::onContinue,
                    )
                }
            }
            PageIndicator(currentPage = currentPage)
        }

        AnimatedVisibility(
            visible = currentPage != OnboardingViewModel.LAST_PAGE,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.sm, end = Spacing.md),
        ) {
            TextButton(onClick = viewModel::onSkip) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    primaryButtonLabel: String,
    primaryButtonEnabled: Boolean = true,
    onPrimaryClick: () -> Unit,
    showSecondaryButton: Boolean = false,
    secondaryButtonLabel: String = "",
    onSecondaryClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = page.headline,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onPrimaryClick,
            enabled = primaryButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = primaryButtonLabel,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        AnimatedVisibility(visible = showSecondaryButton) {
            TextButton(onClick = onSecondaryClick) {
                Text(
                    text = secondaryButtonLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ScanningPage(progress: ScanProgress) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(40.dp))
        LinearProgressIndicator(
            progress = { if (progress.total > 0) progress.scanned / progress.total.toFloat() else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Scanned ${progress.scanned} / ${progress.total} tracks",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(currentPage: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(OnboardingViewModel.PAGE_COUNT) { index ->
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 10.dp else 8.dp)
                    .background(
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private fun requiredPermissionsArray(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun isPermissionGranted(context: Context): Boolean =
    requiredPermissionsArray().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

@Preview
@Composable
private fun OnboardingPageContentPreview() {
    com.hearyet.app.core.ui.theme.HearYetTheme {
        OnboardingPageContent(
            page = onboardingPages[0],
            primaryButtonLabel = "Continue",
            onPrimaryClick = {},
        )
    }
}

@Preview
@Composable
private fun PageIndicatorPreview() {
    com.hearyet.app.core.ui.theme.HearYetTheme {
        PageIndicator(currentPage = 2)
    }
}
