package com.squelch.app.mesh.transport

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class FirestoreTransport(
    private val edPubHex: String
) : Transport {

    companion object {
        private const val TAG = "FirestoreTransport"
        private const val COLLECTION = "messages"
        private const val BATCH_SIZE = 50
    }

    override val name: String = "Firestore"

    private val _incoming = MutableSharedFlow<Transport.TransportFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Transport.TransportFrame> = _incoming.asSharedFlow()

    private var db: FirebaseFirestore? = null
    private var listener: ListenerRegistration? = null
    private var scope: CoroutineScope? = null

    override fun start() {
        try {
            db = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseFirestore init failed: ${e.message}")
            return
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        listener = db!!.collection(COLLECTION)
            .whereEqualTo("recipient", edPubHex)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listener error: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    val sender = doc.getString("sender") ?: return@forEach
                    if (sender == edPubHex) return@forEach

                    val payloadB64 = doc.getString("payload") ?: return@forEach
                    val kind = (doc.getLong("kind") ?: Transport.TransportFrame.KIND_DATA).toInt()

                    try {
                        val payload = Base64.decode(payloadB64, Base64.NO_WRAP)
                        scope?.launch {
                            _incoming.emit(
                                Transport.TransportFrame(
                                    senderEdPubHex = sender,
                                    kind = kind,
                                    payload = payload
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode message: ${e.message}")
                    }

                    try {
                        doc.reference.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete message: ${e.message}")
                    }
                }
            }
        Log.d(TAG, "Firestore transport started, listening for messages to $edPubHex")
    }

    override fun stop() {
        listener?.remove()
        listener = null
        scope?.cancel()
        scope = null
        Log.d(TAG, "Firestore transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray) {
        val fireDb = db ?: return
        val data = mapOf(
            "sender" to edPubHex,
            "recipient" to recipientEdPubHex,
            "payload" to Base64.encodeToString(payload, Base64.NO_WRAP),
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        fireDb.collection(COLLECTION)
            .add(data)
            .addOnSuccessListener { Log.d(TAG, "Message sent to $recipientEdPubHex") }
            .addOnFailureListener { e -> Log.e(TAG, "Send failed: ${e.message}") }
    }
}
