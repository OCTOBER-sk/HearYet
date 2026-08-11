package com.hearyet.app.settings.screens.about

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearyet.app.core.common.extensions.appIcon
import com.hearyet.app.core.ui.R
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.SettingsRow
import com.hearyet.app.core.ui.component.SettingsSectionHeader
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.components.NextTopAppBar
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.settings.utils.rememberTvListFocusRequester
import com.hearyet.app.settings.utils.tvFocusDown
import com.hearyet.app.settings.utils.tvListFocus

private const val NEXT_PLAYER_GITHUB = "https://github.com/anilbeesetti/nextplayer"

// FE §9.9 — the HearYet repository.
private const val HEARYET_GITHUB = "https://github.com/OCTOBER-sk/HearYet"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutPreferencesScreen(
    onLibrariesClick: () -> Unit,
    onViewIntroAgain: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val listFocusRequester = rememberTvListFocusRequester()
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.about_name),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp, modifier = Modifier.tvFocusDown(listFocusRequester)) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = HearYetColors.Background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .tvListFocus(listFocusRequester)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(vertical = 16.dp),
        ) {
            AboutApp(
                onGithubClick = {
                    // FE §9.9 — the app's GitHub button opens the HearYet repo.
                    uriHandler.openUriOrShowToast(
                        uri = HEARYET_GITHUB,
                        context = context,
                    )
                },
                onLibrariesClick = onLibrariesClick,
            )

            SettingsSectionHeader(text = stringResource(id = R.string.about_hearyet))
            Text(
                text = stringResource(id = R.string.about_hearyet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )

            SettingsSectionHeader(text = stringResource(id = R.string.about_open_source))
            Text(
                text = stringResource(id = R.string.about_hearyet_open_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SettingsRow(
                    title = stringResource(R.string.about_view_intro_again),
                    description = stringResource(R.string.about_view_intro_again_description),
                    icon = NextIcons.Play,
                    onClick = onViewIntroAgain,
                    isFirstItem = true,
                    isLastItem = HEARYET_GITHUB.isBlank(),
                )
                // FE §9.9 — open-source note with a link to the HearYet repo.
                if (HEARYET_GITHUB.isNotBlank()) {
                    SettingsRow(
                        title = stringResource(R.string.about_view_hearyet_github),
                        icon = NextIcons.Link,
                        onClick = {
                            uriHandler.openUri(HEARYET_GITHUB)
                        },
                        isFirstItem = false,
                        isLastItem = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // FE §9.9 — professionally worded NextPlayer credit, visually set apart.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.md))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(id = R.string.about_nextplayer_credit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Button(
                        onClick = {
                            uriHandler.openUriOrShowToast(
                                uri = NEXT_PLAYER_GITHUB,
                                context = context,
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HearYetColors.Accent,
                            contentColor = HearYetColors.OnPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(text = stringResource(R.string.about_view_nextplayer))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutApp(
    modifier: Modifier = Modifier,
    onGithubClick: () -> Unit,
    onLibrariesClick: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember { context.appVersion() }
    val appIcon = remember { context.appIcon()?.asImageBitmap() }

    Column(
        modifier = modifier
            .padding(
                vertical = 16.dp,
                horizontal = 8.dp,
            )
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            appIcon?.let {
                Image(
                    bitmap = it,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            }
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onLibrariesClick,
                colors = ButtonDefaults.buttonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .weight(1f),
            ) {
                Text(text = stringResource(R.string.libraries))
            }
            Button(
                onClick = onGithubClick,
                colors = ButtonDefaults.buttonColors(
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    disabledContentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = .12f),
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .12f),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.github))
            }
        }
    }
}

private fun Context.appVersion(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)

    @Suppress("DEPRECATION")
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode
    }

    return "${packageInfo.versionName} ($versionCode)"
}

internal fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri = uri)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
    }
}
