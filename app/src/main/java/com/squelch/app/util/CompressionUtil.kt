package com.squelch.app.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object CompressionUtil {

    fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
            val buffer = ByteArray(1024)
            var len: Int
            while (gis.read(buffer).also { len = it } > 0) {
                bos.write(buffer, 0, len)
            }
        }
        return bos.toByteArray()
    }

    fun compressToBase64(data: ByteArray): String {
        return Base64.encodeToString(compress(data), Base64.NO_WRAP)
    }

    fun decompressFromBase64(b64: String): ByteArray {
        return decompress(Base64.decode(b64, Base64.NO_WRAP))
    }
}
