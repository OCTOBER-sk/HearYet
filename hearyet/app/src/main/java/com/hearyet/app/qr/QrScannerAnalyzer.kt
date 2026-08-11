package com.hearyet.app.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit barcode analyzer for scanning a HearYet session QR code.
 *
 * BE §11 — decodes exactly one QR, then stops.  The frontend owns
 * the camera preview, framing guide, and state-ladder copy; this
 * class only hands off the decoded string reliably.
 */
class QrScannerAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var hasDecoded = false

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDecoded) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        scanner.process(image)
            .addOnSuccessListener { codes ->
                codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue?.let { raw ->
                        hasDecoded = true
                        onDecoded(raw)
                    }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
