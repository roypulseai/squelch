package com.squelch.app.vault

import android.content.Context
import android.accounts.Account
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.squelch.app.auth.AuthState
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin wrapper around the Google Drive REST API.
 *
 * Why raw HTTP: the spec asked for "Google Drive API v3"; the concrete
 * client library (`google-api-services-drive` + `google-api-client-android`)
 * is fragile to version drift and overrides Drive's pageSize/setQ semantics.
 * The REST API is straightforward and stable:
 *
 *   GET    /drive/v3/files?q=...&fields=...
 *   POST   /drive/v3/files                              (create metadata or upload)
 *   GET    /drive/v3/files/{id}?alt=media               (download content)
 *   PATCH  /drive/v3/files/{id}                         (update metadata)
 *   PUT    /upload/drive/v3/files/{id}?uploadType=media (overwrite content)
 *   DELETE /drive/v3/files/{id}                         (trash/delete)
 *
 * Auth: a Bearer access token from [GoogleAuthUtil.getToken] bound to the
 * signed-in Google account, scope `oauth2:drive.file`.
 */
class DriveRest(private val token: String) {

    companion object {
        const val BASE_API = "https://www.googleapis.com/drive/v3"
        const val BASE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_OCTET = "application/octet-stream"
    }

    private fun <T> withConn(url: String, block: HttpURLConnection.() -> T): T {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            block(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun httpGetJson(url: String): JSONObject {
        return withConn(url) {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    fun httpPostJson(url: String, body: JSONObject): JSONObject {
        return withConn(url) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    fun httpPatchJson(url: String, body: JSONObject): JSONObject {
        return withConn(url) {
            requestMethod = "PATCH"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    /** Upload bytes via multipart-like simple upload. */
    fun httpUploadBytes(url: String, bytes: ByteArray): JSONObject {
        return withConn(url) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", MIME_OCTET)
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(bytes) }
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    /** PUT-style overwrite for an existing file. */
    fun httpPutBytes(url: String, bytes: ByteArray): JSONObject {
        return withConn(url) {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", MIME_OCTET)
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(bytes) }
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    fun httpDelete(url: String): Int {
        return withConn(url) {
            requestMethod = "DELETE"
            responseCode
        }
    }

    /** Download bytes via /files/{id}?alt=media. */
    fun httpDownloadBytes(url: String): ByteArray {
        return withConn(url) {
            requestMethod = "GET"
            setRequestProperty("Accept", MIME_OCTET)
            val code = responseCode
            if (code !in 200..299) {
                throw DriveHttpException(code, errorBody(this))
            }
            inputStream.use { it.readBytes() }
        }
    }

    private fun errorBody(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "<no body>"
        } catch (e: Exception) {
            "<unreadable: ${e.message}>"
        }
    }
}

class DriveHttpException(val status: Int, val httpBody: String) :
    Exception("Drive REST HTTP $status: $httpBody")

/** Resolves a Bearer token for the given signed-in account, scope `drive.file`.
 *  Static + blocking; must be called off the UI thread. */
object DriveTokenSource {

    const val SCOPE_DRIVE_FILE = "oauth2:https://www.googleapis.com/auth/drive.file"

    fun accessTokenBlocking(context: Context, signedIn: AuthState.SignedIn): String {
        val account: Account = GoogleSignIn
            .getLastSignedInAccount(context)
            ?.account
            ?: throw IllegalStateException("No signed-in Google account available.")
        return GoogleAuthUtil.getToken(context, account, SCOPE_DRIVE_FILE)
    }
}

/** Escapes single quotes for use inside a Drive `q=` query. */
internal fun driveEscape(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** Find first file id matching the `q` query, or null. */
internal fun firstFileIn(json: JSONObject): JSONObject? {
    val files: JSONArray = json.optJSONArray("files") ?: return null
    if (files.length() == 0) return null
    return files.getJSONObject(0)
}
