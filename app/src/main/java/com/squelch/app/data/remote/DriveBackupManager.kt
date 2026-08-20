package com.squelch.app.data.remote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DriveBackupManager"
        private const val VAULT_FILE_NAME = "squelch_vault_backup.bin"
        private const val MESSAGE_FILE_NAME = "squelch_messages_backup.db"
        private const val FOLDER_NAME = "Squelch"
        private const val PREFS_NAME = "drive_backup_prefs"
        private const val KEY_ACCESS_TOKEN = "drive_access_token"
        private const val KEY_REFRESH_TOKEN = "drive_refresh_token"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val API_BASE = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FIELDS = "id,name"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val httpClient = OkHttpClient.Builder().build()

    private val multipartType = "application/octet-stream".toMediaType()
    private val metadataType = "application/json; charset=UTF-8".toMediaType()

    // ── Sign-in ──────────────────────────────────────────────────────────────

    fun getDriveSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun onDriveSignInResult(data: Intent?): Boolean {
        if (data == null) return false
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val token = GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:$DRIVE_SCOPE"
            )
            storeToken(token)
            Log.d(TAG, "Drive sign-in successful")
            true
        } catch (e: ApiException) {
            Log.e(TAG, "Drive sign-in failed: ${e.statusCode}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Drive sign-in error", e)
            false
        }
    }

    // ── Token management ─────────────────────────────────────────────────────

    suspend fun ensureDriveAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken() ?: return@withContext false
            val request = Request.Builder()
                .url("$API_BASE?spaces=appDataFolder&fields=$FIELDS&pageSize=1")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) return@withContext true
            if (response.code == 401) {
                val refreshed = refreshAccessToken()
                if (refreshed) return@withContext true
            }
            Log.w(TAG, "Drive access check failed: ${response.code}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "ensureDriveAccess error", e)
            false
        }
    }

    private fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (token != null) return token
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val freshToken = GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:$DRIVE_SCOPE"
            )
            storeToken(freshToken)
            freshToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token via GoogleAuthUtil", e)
            null
        }
    }

    private fun refreshAccessToken(): Boolean {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
            val freshToken = GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:$DRIVE_SCOPE"
            )
            storeToken(freshToken)
            Log.d(TAG, "Access token refreshed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            clearToken()
            false
        }
    }

    private fun storeToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    private fun clearToken() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun hasBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken() ?: return@withContext false
            val vaultExists = fileExists(token, VAULT_FILE_NAME)
            val messagesExists = fileExists(token, MESSAGE_FILE_NAME)
            vaultExists || messagesExists
        } catch (e: Exception) {
            Log.e(TAG, "hasBackup error", e)
            false
        }
    }

    suspend fun backupVault(vaultBlob: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken() ?: return@withContext false
            deleteFileByName(token, VAULT_FILE_NAME)
            uploadFile(token, VAULT_FILE_NAME, vaultBlob)
        } catch (e: Exception) {
            Log.e(TAG, "backupVault error", e)
            false
        }
    }

    suspend fun backupMessages(dbFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!dbFile.exists()) {
                Log.e(TAG, "DB file does not exist: ${dbFile.absolutePath}")
                return@withContext false
            }
            val token = getAccessToken() ?: return@withContext false
            val bytes = dbFile.readBytes()
            deleteFileByName(token, MESSAGE_FILE_NAME)
            uploadFile(token, MESSAGE_FILE_NAME, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "backupMessages error", e)
            false
        }
    }

    suspend fun restoreVault(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken() ?: return@withContext null
            downloadFileByName(token, VAULT_FILE_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "restoreVault error", e)
            null
        }
    }

    suspend fun restoreMessages(): File? = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken() ?: return@withContext null
            val bytes = downloadFileByName(token, MESSAGE_FILE_NAME) ?: return@withContext null
            val cacheDir = File(context.cacheDir, "drive_restore")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val outFile = File(cacheDir, MESSAGE_FILE_NAME)
            outFile.writeBytes(bytes)
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "restoreMessages error", e)
            null
        }
    }

    // ── Drive API helpers ────────────────────────────────────────────────────

    private fun fileExists(token: String, fileName: String): Boolean {
        val query = "name='$fileName' and trashed=false"
        val url = "$API_BASE?spaces=appDataFolder&fields=$FIELDS&q=${query}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return false
        val body = response.body?.string() ?: return false
        val json = JSONObject(body)
        return json.optJSONArray("files")?.length()?.let { it > 0 } ?: false
    }

    private fun findFileId(token: String, fileName: String): String? {
        val query = "name='$fileName' and trashed=false"
        val url = "$API_BASE?spaces=appDataFolder&fields=$FIELDS&q=${query}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val files = json.optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).optString("id") else null
    }

    private fun deleteFileByName(token: String, fileName: String) {
        try {
            val fileId = findFileId(token, fileName) ?: return
            val request = Request.Builder()
                .url("$API_BASE/$fileId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Deleted old file: $fileName")
            } else {
                Log.w(TAG, "Failed to delete $fileName: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFileByName error for $fileName", e)
        }
    }

    private fun uploadFile(token: String, fileName: String, content: ByteArray): Boolean {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().put("appDataFolder"))
        }

        val metadataBody = metadata.toString()
            .toRequestBody(metadataType)

        val contentBody = content.toRequestBody(multipartType)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata", null,
                metadataBody
            )
            .addFormDataPart(
                "file", fileName,
                contentBody
            )
            .build()

        val url = "$UPLOAD_BASE?uploadType=multipart&fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(multipart)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
        if (!response.isSuccessful) {
            Log.e(TAG, "Upload failed for $fileName: ${response.code} $responseBody")
            return false
        }
        Log.d(TAG, "Uploaded $fileName (${content.size} bytes)")
        return true
    }

    private fun downloadFileByName(token: String, fileName: String): ByteArray? {
        val fileId = findFileId(token, fileName)
        if (fileId == null) {
            Log.w(TAG, "File not found for download: $fileName")
            return null
        }

        val request = Request.Builder()
            .url("$API_BASE/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Download failed for $fileName: ${response.code}")
            return null
        }
        val bytes = response.body?.bytes()
        if (bytes == null) {
            Log.w(TAG, "Downloaded file is empty: $fileName")
            return null
        }
        Log.d(TAG, "Downloaded $fileName (${bytes.size} bytes)")
        return bytes
    }
}
