package com.hearyet.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors

@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = HearYetColors.Accent,
        inactiveTrackColor = HearYetColors.SurfaceOutline,
        thumbColor = HearYetColors.Accent,
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        thumb = {
            // M3's Slider vertically centers the thumb using a fixed 20dp thumb
            // width, so a bare 12dp ball sits above the track line. Keep the
            // touch container at the expected 20dp and center the visible ball
            // inside it — the ball then lands exactly on the track.
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(HearYetColors.Accent, CircleShape),
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(2.dp),
                colors = colors,
                enabled = enabled,
                // Half the 12dp thumb — the ball sits centered over the line's ends
                // instead of overhanging them.
                thumbTrackGapSize = 6.dp,
                trackInsideCornerSize = 0.dp,
            )
        },
    )
}

@Preview
@Composable
private fun ThinSliderPreview() {
    ThinSlider(value = 0.65f, onValueChange = {}, enabled = true)
}
