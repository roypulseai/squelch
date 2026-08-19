package com.squelch.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.util.Base64

object QrCodec {

    private const val PREFIX = "SQCH:"

    fun encode(contact: QrContact): String {
        val json = JSONObject().apply {
            put("edPub", contact.edPub)
            put("xPub", contact.xPub)
            put("cs", contact.callsign)
            put("dn", contact.displayName)
        }
        val raw = json.toString()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        return "$PREFIX$encoded"
    }

    fun decode(qrContent: String): QrContact? {
        return try {
            val raw = if (qrContent.startsWith(PREFIX)) {
                val encoded = qrContent.removePrefix(PREFIX)
                String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            } else {
                qrContent
            }
            val json = JSONObject(raw)
            QrContact(
                edPub = json.getString("edPub"),
                xPub = json.getString("xPub"),
                callsign = json.optString("cs", ""),
                displayName = json.optString("dn", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun generateBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun selfQr(contact: QrContact, size: Int = 512): Bitmap {
        return generateBitmap(encode(contact), size)
    }
}
