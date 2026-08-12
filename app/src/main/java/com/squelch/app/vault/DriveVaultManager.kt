package com.squelch.app.vault

import android.content.Context
import com.squelch.app.auth.AuthState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * High-level Drive operations for /squelch/vault.enc, using the raw REST
 * helper [DriveRest].
 *
 * Operations:
 *   - findOrCreateFolder()    : ensure /squelch/ exists. Idempotent.
 *   - uploadVault(bytes)      : overwrite vault.enc inside /squelch/.
 *   - downloadVault()         : returns the encrypted vault bytes, or null.
 *   - deleteVault()           : wipe the Drive vault file (used on identity reset).
 *
 * Token acquisition is performed fresh per call so token refreshes work
 * transparently on long-lived sessions.
 */
class DriveVaultManager(
    private val context: Context,
    private val signedIn: AuthState.SignedIn,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val APP_FOLDER = "squelch"
        const val VAULT_FILE = "vault.enc"
    }

    data class Folder(val id: String, val name: String)
    data class VaultFile(val id: String, val bytes: ByteArray)

    private fun drive(): DriveRest =
        DriveRest(DriveTokenSource.accessTokenBlocking(context, signedIn))

    /** Ensure /squelch/ exists. Idempotent. */
    suspend fun findOrCreateFolder(): Folder = withContext(io) {
        findFolder(APP_FOLDER) ?: createFolder(APP_FOLDER)
    }

    private suspend fun findFolder(name: String): Folder? = withContext(io) {
        val q = "name='${driveEscape(name)}' " +
                "and mimeType='${DriveRest.MIME_FOLDER}' " +
                "and trashed=false"
        val url = "${DriveRest.BASE_API}/files" +
                "?q=$q" +
                "&fields=files(id,name)" +
                "&spaces=drive" +
                "&pageSize=1"
        val json = drive().httpGetJson(url)
        firstFileIn(json)?.let { Folder(it.getString("id"), it.getString("name")) }
    }

    private suspend fun createFolder(name: String): Folder = withContext(io) {
        val body = JSONObject()
            .put("name", name)
            .put("mimeType", DriveRest.MIME_FOLDER)
        val url = "${DriveRest.BASE_API}/files?fields=id,name"
        val result = drive().httpPostJson(url, body)
        Folder(result.getString("id"), result.getString("name"))
    }

    /** Find vault.enc inside [folderId] and return its content. */
    suspend fun findVaultFile(folderId: String): VaultFile? = withContext(io) {
        val q = "name='${driveEscape(VAULT_FILE)}' " +
                "and '$folderId' in parents " +
                "and trashed=false"
        val url = "${DriveRest.BASE_API}/files" +
                "?q=$q" +
                "&fields=files(id,name)" +
                "&spaces=drive" +
                "&pageSize=1"
        val json = drive().httpGetJson(url)
        val first = firstFileIn(json) ?: return@withContext null
        val id = first.getString("id")
        VaultFile(id, readFile(id))
    }

    /** Upload (overwrite) the vault.enc inside [folderId]. */
    suspend fun uploadVault(folderId: String, bytes: ByteArray): VaultFile = withContext(io) {
        val existing = findVaultFile(folderId)
        val rest = drive()
        val result = if (existing != null) {
            // Overwrite content via PUT to /upload/files/{id}?uploadType=media.
            val url = "${DriveRest.BASE_UPLOAD}/files/${existing.id}" +
                    "?uploadType=media" +
                    "&fields=id,name"
            rest.httpPutBytes(url, bytes)
        } else {
            // Create metadata + content via POST /upload/files (multipart not
            // needed; we use single-part "media" plus a parent).
            val metadata = JSONObject()
                .put("name", VAULT_FILE)
                .put("mimeType", DriveRest.MIME_OCTET)
                .put("parents", org.json.JSONArray().put(folderId))
            val url = "${DriveRest.BASE_UPLOAD}/files" +
                    "?uploadType=multipart" +
                    "&fields=id,name"
            uploadMultipart(url, metadata, bytes)
        }
        VaultFile(result.getString("id"), bytes)
    }

    /** Read vault.enc into memory. Caller decrypts. */
    suspend fun downloadVault(): ByteArray? = withContext(io) {
        val folder = findOrCreateFolder()
        findVaultFile(folder.id)?.bytes
    }

    /** Wipe the vault file (used on identity reset). */
    suspend fun deleteVault(): Boolean = withContext(io) {
        val folder = findFolder(APP_FOLDER) ?: return@withContext false
        val existing = findVaultFile(folder.id) ?: return@withContext true
        val code = drive().httpDelete("${DriveRest.BASE_API}/files/${existing.id}")
        code in 200..299
    }

    private suspend fun readFile(fileId: String): ByteArray = withContext(io) {
        val url = "${DriveRest.BASE_API}/files/$fileId?alt=media"
        drive().httpDownloadBytes(url)
    }

    /**
     * Minimal multipart upload ("related" variant). Drive v3 requires the
     * first part to be application/json metadata, second the binary body,
     * separated by a boundary. We pick `bnd-X` and build the parts on the fly.
     */
    private fun uploadMultipart(url: String, metadata: JSONObject, bytes: ByteArray): JSONObject {
        val boundary = "squelch-$$-${System.nanoTime()}"
        val delimiter = "\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8)
        val closing = "\r\n--$boundary--".toByteArray(Charsets.UTF_8)
        val part1Header =
            ("Content-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(Charsets.UTF_8)
        val part2Header =
            ("Content-Type: " + DriveRest.MIME_OCTET + "\r\n\r\n").toByteArray(Charsets.UTF_8)

        val parts = mutableListOf<ByteArray>().apply {
            add(delimiter)
            add(part1Header)
            add(metadata.toString().toByteArray(Charsets.UTF_8))
            add(delimiter)
            add(part2Header)
            add(bytes)
            add(closing)
        }
        val size = parts.sumOf { it.size }
        val body = java.io.ByteArrayOutputStream(size).apply {
            parts.forEach { chunk -> write(chunk) }
        }.toByteArray()

        // The DriveRest wrapper builds HttpURLConnection; for multipart we
        // need to attach the boundary header ourselves. Easiest: extend the
        // helper with a raw overload. For now we do it inline.
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Authorization",
                "Bearer ${DriveTokenSource.accessTokenBlocking(context, signedIn)}")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setFixedLengthStreamingMode(body.size)
        }
        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
            }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
