package com.hearyet.app.settings.screens.gesture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hearyet.app.settings.utils.rememberTvListFocusRequester
import com.hearyet.app.settings.utils.tvFocusDown
import com.hearyet.app.settings.utils.tvListFocus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import com.hearyet.app.core.ui.color.HearYetColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearyet.app.core.common.extensions.round
import com.hearyet.app.core.common.extensions.toString
import com.hearyet.app.core.model.DoubleTapGesture
import com.hearyet.app.core.model.PlayerPreferences
import com.hearyet.app.core.ui.R
import com.hearyet.app.core.ui.component.SettingsSectionHeader
import com.hearyet.app.core.ui.components.NextDialogWithDoneAndCancelButtons
import com.hearyet.app.core.ui.components.NextTopAppBar
import com.hearyet.app.core.ui.components.PreferenceSlider
import com.hearyet.app.core.ui.components.PreferenceSwitch
import com.hearyet.app.core.ui.components.PreferenceSwitchWithDivider
import com.hearyet.app.core.ui.components.RadioTextButton
import com.hearyet.app.core.ui.component.ThinSlider
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.preview.DayNightPreview
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.settings.composables.OptionsDialog
import com.hearyet.app.settings.extensions.name

@Composable
fun GesturePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: GesturePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GesturePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GesturePreferencesContent(
    uiState: GesturePreferencesUiState,
    onEvent: (GesturePreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
) {
    val listFocusRequester = rememberTvListFocusRequester()
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.gestures),
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
                .verticalScroll(state = rememberScrollState())
                .tvListFocus(listFocusRequester)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            SettingsSectionHeader(text = stringResource(id = R.string.gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.seek_gesture),
                    description = stringResource(id = R.string.seek_gesture_description),
                    icon = NextIcons.SwipeHorizontal,
                    isChecked = uiState.preferences.useSeekControls,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleUseSeekControls) },
                    isFirstItem = true
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_gesture_sensitivity),
                    description = uiState.preferences.seekSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    enabled = uiState.preferences.useSeekControls,
                    value = uiState.preferences.seekSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateSeekSensitivity(it)) },
                    trailingContent = {
                        FilledIconButton(
                            enabled = uiState.preferences.useSeekControls,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateSeekSensitivity(PlayerPreferences.DEFAULT_SEEK_SENSITIVITY)) }
                        ) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_sensitivity),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.brightness_gesture),
                    description = stringResource(id = R.string.brightness_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.enableBrightnessSwipeGesture,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnableBrightnessSwipeGesture) },
                )
                PreferenceSlider(
                    title = stringResource(R.string.brightness_gesture_sensitivity),
                    description = uiState.preferences.brightnessGestureSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    enabled = uiState.preferences.enableBrightnessSwipeGesture,
                    value = uiState.preferences.brightnessGestureSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateBrightnessGestureSensitivity(it)) },
                    trailingContent = {
                        FilledIconButton(
                            enabled = uiState.preferences.enableBrightnessSwipeGesture,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateBrightnessGestureSensitivity(PlayerPreferences.DEFAULT_BRIGHTNESS_GESTURE_SENSITIVITY)) }
                        ) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_brightness_gesture_sensitivity),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.volume_gesture),
                    description = stringResource(id = R.string.volume_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.enableVolumeSwipeGesture,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnableVolumeSwipeGesture) },
                )
                PreferenceSlider(
                    title = stringResource(R.string.volume_gesture_sensitivity),
                    description = uiState.preferences.volumeGestureSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    enabled = uiState.preferences.enableVolumeSwipeGesture,
                    value = uiState.preferences.volumeGestureSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateVolumeGestureSensitivity(it)) },
                    trailingContent = {
                        FilledIconButton(
                            enabled = uiState.preferences.enableVolumeSwipeGesture,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateVolumeGestureSensitivity(PlayerPreferences.DEFAULT_VOLUME_GESTURE_SENSITIVITY)) }
                        ) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_volume_gesture_sensitivity),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.zoom_gesture),
                    description = stringResource(id = R.string.zoom_gesture_description),
                    icon = NextIcons.Pinch,
                    isChecked = uiState.preferences.useZoomControls,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleUseZoomControls) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.pan_gesture),
                    description = stringResource(id = R.string.pan_gesture_description),
                    icon = NextIcons.Pan,
                    enabled = uiState.preferences.useZoomControls,
                    isChecked = uiState.preferences.enablePanGesture,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnablePanGesture) },
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.double_tap),
                    description = stringResource(id = R.string.double_tap_description),
                    icon = NextIcons.DoubleTap,
                    isChecked = (uiState.preferences.doubleTapGesture != DoubleTapGesture.NONE),
                    onChecked = { onEvent(GesturePreferencesUiEvent.ToggleDoubleTapGesture) },
                    onClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(GesturePreferenceDialog.DoubleTapDialog)) },
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.long_press_gesture),
                    description = stringResource(id = R.string.long_press_gesture_desc, uiState.preferences.longPressControlsSpeed),
                    icon = NextIcons.Tap,
                    isChecked = uiState.preferences.useLongPressControls,
                    onChecked = { onEvent(GesturePreferencesUiEvent.ToggleUseLongPressControls) },
                    onClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(GesturePreferenceDialog.LongPressControlsSpeedDialog)) },
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_increment),
                    description = stringResource(R.string.seconds, uiState.preferences.seekIncrement),
                    isLastItem = true,
                    icon = NextIcons.Replay,
                    value = uiState.preferences.seekIncrement.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateSeekIncrement(it.toInt())) },
                    onReset = { onEvent(GesturePreferencesUiEvent.UpdateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT)) },
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(GesturePreferencesUiEvent.UpdateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_increment),
                            )
                        }
                    },
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                GesturePreferenceDialog.DoubleTapDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.double_tap),
                        onDismissClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(DoubleTapGesture.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == uiState.preferences.doubleTapGesture),
                                onClick = {
                                    onEvent(GesturePreferencesUiEvent.UpdateDoubleTapGesture(it))
                                    onEvent(GesturePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                GesturePreferenceDialog.LongPressControlsSpeedDialog -> {
                    var longPressControlsSpeed by remember {
                        mutableFloatStateOf(uiState.preferences.longPressControlsSpeed)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.long_press_gesture),
                        onDoneClick = {
                            onEvent(GesturePreferencesUiEvent.UpdateLongPressControlsSpeed(longPressControlsSpeed))
                            onEvent(GesturePreferencesUiEvent.ShowDialog(null))
                        },
                        onDismissClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(null)) },
                        content = {
                            Text(
                                text = "$longPressControlsSpeed",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            ThinSlider(
                                value = longPressControlsSpeed,
                                onValueChange = { longPressControlsSpeed = it.round(1) },
                                valueRange = 0.2f..4.0f,
                            )
                        },
                    )
                }
            }
        }
    }
}

@DayNightPreview
@Composable
private fun GesturePreferencesScreenPreview() {
    HearYetTheme {
        GesturePreferencesContent(
            uiState = GesturePreferencesUiState(),
            onEvent = {},
        )
    }
}
