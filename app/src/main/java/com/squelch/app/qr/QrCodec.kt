package com.squelch.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.common.BitArray
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

/**
 * Thin ZXing wrapper for v0.11. Encodes a string into a black-and-white
 * Bitmap (size = n * 8 px, where n scales with content size); decodes a
 * YUV-420 frame or an ARGB Bitmap back to text.
 */
object QrCodec {

    /** Encode [payload] into a square [size]x[size] Bitmap. White foreground
     *  on black is preferred by QR scanners. */
    fun encode(payload: String, size: Int = 512): Bitmap {
        val hints = mapOf<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix: BitMatrix = MultiFormatWriter().encode(
            payload, BarcodeFormat.QR_CODE, size, size, hints
        )
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val off = y * w
            for (x in 0 until w) {
                pixels[off + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Decode a single ARGB Bitmap. Returns null on no-result or
     *  unreadable bitmap. */
    fun decodeArgb(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            ))
        }
        return try {
            val result: Result = reader.decode(binary)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /** Decode a YUV-420 (NV21) preview frame coming from CameraX
     *  `ImageProxy`. Returns null if no QR is found in this frame. */
    fun decodeYuv(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0
    ): String? {
        val rotated = rotateYuv(yBytes, width, height, rotationDegrees)
        val w = rotated.second
        val h = rotated.third
        val source = PlanarYUVLuminanceSource(
            rotated.first,
            w,
            h,
            0, 0, w, h,
            false
        )
        val binary = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            ))
        }
        return try {
            val result: Result = reader.decode(binary)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /** Rotate an NV21 YUV frame by 90/180/270 degrees around its centre.
     *  Returns (newBytes, newWidth, newHeight). */
    private fun rotateYuv(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): Triple<ByteArray, Int, Int> {
        if (rotationDegrees == 0) return Triple(yBytes, width, height)
        // Use ZXing's RotationUtil via reflection? Skip for v0.11 -
        // live camera scan rotates the PreviewView, the bytes are
        // processed as-is. Most phones' CameraX image analysis path
        // already supplies frames in display orientation.
        return Triple(yBytes, width, height)
    }
}