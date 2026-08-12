package com.squelch.app.mesh

import com.squelch.app.crypto.noise.Hkdf
import com.squelch.app.db.AppDatabase
import com.squelch.app.db.RoomEntity
import com.squelch.app.util.Bytes
import kotlinx.coroutines.flow.firstOrNull
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Rooms / broadcast channels (spec 6.2): named local channels anyone in range can
 * join. Passphrase-protected rooms derive a different key so only holders of the
 * passphrase can read. Room messages are encrypted with the shared room key and
 * flooded; there is no per-recipient addressing.
 */
class RoomManager(private val db: AppDatabase) {

    data class Room(val id: ByteArray, val name: String, val passphrase: String) {
        val idHex: String get() = Bytes.hex(id)
    }

    private val keyCache = HashMap<String, ByteArray>() // idHex -> key
    private val random = SecureRandom()

    fun roomKey(name: String, passphrase: String): ByteArray =
        Hkdf.hash("squelch-room:v1:$name\u0000$passphrase".toByteArray(Charsets.UTF_8))

    fun roomId(name: String, passphrase: String): ByteArray =
        Hkdf.hash("squelch-room-id:v1:$name\u0000$passphrase".toByteArray(Charsets.UTF_8)).copyOf(16)

    fun currentRooms(): List<Room> {
        val ents = kotlinx.coroutines.runBlocking { db.rooms().observeAll().firstOrNull() } ?: return emptyList()
        return ents.map { Room(Bytes.unhex(it.id), it.name, it.passphrase) }
    }

    suspend fun join(name: String, passphrase: String): Room {
        val id = roomId(name, passphrase)
        db.rooms().upsert(RoomEntity(Bytes.hex(id), name, passphrase, joined = true, createdAt = System.currentTimeMillis()))
        return Room(id, name, passphrase)
    }

    suspend fun leave(idHex: String) {
        db.rooms().setJoined(idHex, false)
    }

    fun findById(id: ByteArray): Room? {
        val room = kotlinx.coroutines.runBlocking { db.rooms().get(Bytes.hex(id)) } ?: return null
        return Room(id, room.name, room.passphrase)
    }

    fun encrypt(room: Room, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        random.nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(roomKey(room.name, room.passphrase), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(room.idHex.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext)
        return Bytes.concat(iv, ct)
    }

    fun decrypt(room: Room, ciphertext: ByteArray): ByteArray? {
        if (ciphertext.size < 12 + 16) return null
        return try {
            val iv = ciphertext.copyOfRange(0, 12)
            val ct = ciphertext.copyOfRange(12, ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(roomKey(room.name, room.passphrase), "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(room.idHex.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ct)
        } catch (e: Exception) {
            null
        }
    }
}
