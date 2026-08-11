package com.hearyet.app.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The QR display container. A QR code needs strong light/dark contrast to scan
 * reliably, so this is the one deliberate exception to the all-dark palette:
 * the QR bitmap sits on a small [MaterialTheme.colorScheme.onBackground]-toned
 * rounded card, inset within the otherwise-dark bottom sheet.
 */
@Composable
fun QrFrame(
    bitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(220.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Session QR code",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Preview
@Composable
private fun QrFramePreview() {
    QrFrame()
}
