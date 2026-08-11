package com.hearyet.app.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates a QR code bitmap from a [SessionPayload]-encoded string.
 *
 * BE §11 — the QR always encodes the full payload so a scan and a
 * manually-typed code resolve to the same connection path.
 */
object QrGenerator {

    /**
     * Generate a QR code bitmap for [payload].
     *
     * @param payload  A [SessionPayloadCodec.encode] result (BE §3).
     * @param sizePx   Width and height in pixels (default 512).
     */
    fun generate(payload: String, sizePx: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                )
            }
        }
        return bitmap
    }
}
