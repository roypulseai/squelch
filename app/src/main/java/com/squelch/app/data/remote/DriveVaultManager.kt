package com.squelch.app.data.remote

import com.squelch.app.auth.AuthState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveVaultManager @Inject constructor() {

    companion object {
        const val FOLDER_NAME = "squelch"
        const val VAULT_FILE_NAME = "vault.enc"
    }

    private var rest: DriveRest? = null

    fun init(auth: AuthState.SignedIn) {
        rest = DriveRest(auth)
    }

    private fun requireRest(): DriveRest = rest ?: throw IllegalStateException("DriveVaultManager not initialized - call init() first")

    suspend fun findOrCreateFolder(): DriveRest.DriveFile {
        val r = requireRest()
        val existing = r.listFiles(name = FOLDER_NAME, mimeType = "application/vnd.google-apps.folder")
        return if (existing.isNotEmpty()) {
            existing.first()
        } else {
            r.createFolder(FOLDER_NAME) ?: throw IllegalStateException("Failed to create /squelch/ folder")
        }
    }

    suspend fun findVaultFile(folderId: String): DriveRest.DriveFile? {
        val files = requireRest().listFiles(folderId = folderId, name = VAULT_FILE_NAME)
        return files.firstOrNull()
    }

    suspend fun uploadVault(folderId: String, bytes: ByteArray) {
        val r = requireRest()
        val existing = findVaultFile(folderId)
        if (existing != null) {
            r.deleteFile(existing.id)
        }
        r.uploadFile(
            name = VAULT_FILE_NAME,
            parentFolderId = folderId,
            content = bytes,
            mimeType = "application/octet-stream"
        )
    }

    suspend fun downloadVault(): ByteArray? {
        val r = requireRest()
        val folder = findOrCreateFolder()
        val vaultFile = findVaultFile(folder.id) ?: return null
        return r.downloadFile(vaultFile.id)
    }

    suspend fun deleteVault(folderId: String) {
        val vaultFile = findVaultFile(folderId) ?: return
        requireRest().deleteFile(vaultFile.id)
    }
}
