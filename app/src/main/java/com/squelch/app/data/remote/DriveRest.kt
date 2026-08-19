package com.squelch.app.data.remote

import android.util.Log
import com.squelch.app.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DriveRest(private val auth: AuthState.SignedIn) {

    companion object {
        private const val TAG = "DriveRest"
        private const val BASE_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3"
    }

    private fun authHeader(): String = "Bearer ${auth.idToken}"

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json"
    ): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", authHeader())
        conn.setRequestProperty("Content-Type", contentType)
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000

        if (body != null) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
        }

        val code = conn.responseCode
        val stream: InputStream = if (code in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream ?: conn.inputStream
        }

        val response = stream.bufferedReader().use(BufferedReader::readText)
        conn.disconnect()
        return code to response
    }

    suspend fun listFiles(folderId: String? = null, name: String? = null, mimeType: String? = null): List<DriveFile> =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()
            if (folderId != null) queryParts.add("'$folderId' in parents")
            if (name != null) queryParts.add("name = '$name'")
            if (mimeType != null) queryParts.add("mimeType = '$mimeType'")
            queryParts.add("trashed = false")

            val q = queryParts.joinToString(" and ")
            val url = "$BASE_URL/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name,mimeType,size)"

            val (code, response) = request("GET", url)
            if (code != 200) {
                Log.e(TAG, "listFiles failed: $code $response")
                return@withContext emptyList()
            }

            val json = JSONObject(response)
            val files = json.optJSONArray("files") ?: return@withContext emptyList()
            (0 until files.length()).map { i ->
                val f = files.getJSONObject(i)
                DriveFile(
                    id = f.getString("id"),
                    name = f.getString("name"),
                    mimeType = f.optString("mimeType", ""),
                    size = f.optLong("size", 0)
                )
            }
        }

    suspend fun createFolder(name: String, parentFolderId: String? = null): DriveFile? =
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                if (parentFolderId != null) put("parents", org.json.JSONArray().put(parentFolderId))
            }
            val url = "$BASE_URL/files?fields=id,name"
            val (code, response) = request("POST", url, metadata.toString())
            if (code !in 200..299) {
                Log.e(TAG, "createFolder failed: $code $response")
                return@withContext null
            }
            val json = JSONObject(response)
            DriveFile(id = json.getString("id"), name = json.getString("name"), mimeType = "application/vnd.google-apps.folder", size = 0)
        }

    suspend fun uploadFile(name: String, parentFolderId: String, content: ByteArray, mimeType: String = "application/octet-stream"): DriveFile? =
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                put("name", name)
                put("parents", org.json.JSONArray().put(parentFolderId))
            }
            val boundary = "----SquelchBoundary${System.currentTimeMillis()}"
            val body = buildMultipartBoundary(boundary, metadata.toString(), content, mimeType)

            val url = "$UPLOAD_URL/upload/files?uploadType=multipart&fields=id,name,size"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", authHeader())
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.connectTimeout = 60_000
            conn.readTimeout = 60_000
            conn.doOutput = true

            conn.outputStream.use { os -> os.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            val response = stream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()

            if (code !in 200..299) {
                Log.e(TAG, "uploadFile failed: $code $response")
                return@withContext null
            }
            val json = JSONObject(response)
            DriveFile(id = json.getString("id"), name = json.getString("name"), mimeType = mimeType, size = content.size.toLong())
        }

    suspend fun downloadFile(fileId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/files/$fileId?alt=media"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", authHeader())
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000

            val code = conn.responseCode
            if (code != 200) {
                Log.e(TAG, "downloadFile failed: $code")
                conn.disconnect()
                return@withContext null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            bytes
        }

    suspend fun deleteFile(fileId: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/files/$fileId"
            val (code, _) = request("DELETE", url)
            code in 200..299
        }

    private fun buildMultipartBoundary(boundary: String, metadataJson: String, fileContent: ByteArray, fileMimeType: String): ByteArray {
        val metaPart = ("--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadataJson + "\r\n").toByteArray(Charsets.UTF_8)

        val filePart = ("--$boundary\r\n" +
            "Content-Type: $fileMimeType\r\n" +
            "Content-Transfer-Encoding: binary\r\n\r\n").toByteArray(Charsets.UTF_8)

        val end = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val total = metaPart.size + filePart.size + fileContent.size + end.size
        val result = ByteArray(total)
        var pos = 0
        System.arraycopy(metaPart, 0, result, pos, metaPart.size); pos += metaPart.size
        System.arraycopy(filePart, 0, result, pos, filePart.size); pos += filePart.size
        System.arraycopy(fileContent, 0, result, pos, fileContent.size); pos += fileContent.size
        System.arraycopy(end, 0, result, pos, end.size)
        return result
    }

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: Long
    )
}
