package com.squelch.app.mesh.transport

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
        Log.d(TAG, "Starting listener for recipient=$edPubHex")

        listener = db!!.collection(COLLECTION)
            .whereEqualTo("recipient", edPubHex)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents
                if (docs.isNullOrEmpty()) {
                    Log.d(TAG, "Listener fired: 0 documents")
                    return@addSnapshotListener
                }
                Log.d(TAG, "Listener fired: ${docs.size} documents")

                for (doc in docs) {
                    try {
                        val sender = doc.getString("sender") ?: continue
                        if (sender == edPubHex) {
                            doc.reference.delete()
                            continue
                        }

                        val payloadB64 = doc.getString("payload") ?: continue
                        val payload = Base64.decode(payloadB64, Base64.NO_WRAP)
                        val senderName = doc.getString("senderName") ?: sender.take(8)
                        val recipientUid = doc.getString("recipientUid") ?: ""
                        val kind = (doc.getLong("kind") ?: Transport.TransportFrame.KIND_DATA).toInt()

                        val senderEmail = doc.getString("senderEmail") ?: ""
                        val msgId = doc.getString("msgId")

                        Log.d(TAG, "Incoming from $sender ($senderName), ${payload.size} bytes")

                        scope?.launch {
                            _incoming.emit(
                                Transport.TransportFrame(
                                    senderEdPubHex = sender,
                                    kind = kind,
                                    payload = payload,
                                    senderName = senderName,
                                    senderEmail = senderEmail,
                                    msgId = msgId
                                )
                            )
                            Log.d(TAG, "Emitted frame from $sender")
                        }

                        doc.reference.delete()
                            .addOnSuccessListener { Log.d(TAG, "Deleted consumed doc") }
                            .addOnFailureListener { e -> Log.e(TAG, "Delete failed: ${e.message}") }
                    } catch (e: Exception) {
                        Log.e(TAG, "Process doc failed: ${e.message}", e)
                    }
                }
            }
        Log.d(TAG, "Firestore transport started for $edPubHex")
    }

    override fun stop() {
        listener?.remove()
        listener = null
        scope?.cancel()
        scope = null
        Log.d(TAG, "Firestore transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray, kind: Int) {
        val fireDb = db ?: run {
            Log.e(TAG, "Cannot send: Firestore not initialized")
            return
        }
        val data = mapOf(
            "sender" to edPubHex,
            "recipient" to recipientEdPubHex,
            "payload" to Base64.encodeToString(payload, Base64.NO_WRAP),
            "timestamp" to com.google.firebase.Timestamp.now(),
            "kind" to kind
        )
        Log.d(TAG, "Sending to $recipientEdPubHex (${payload.size} bytes)")
        fireDb.collection(COLLECTION)
            .add(data)
            .addOnSuccessListener { Log.d(TAG, "Sent successfully") }
            .addOnFailureListener { e -> Log.e(TAG, "Send failed: ${e.message}", e) }
    }

    fun sendWithMeta(
        recipientEdPubHex: String,
        recipientUid: String,
        senderName: String,
        senderEmail: String,
        payload: ByteArray,
        kind: Int = Transport.TransportFrame.KIND_DATA,
        msgId: String? = null
    ) {
        val fireDb = db ?: run {
            Log.e(TAG, "Cannot send: Firestore not initialized")
            return
        }
        val data = mutableMapOf<String, Any>(
            "sender" to edPubHex,
            "recipient" to recipientEdPubHex,
            "recipientUid" to recipientUid,
            "senderName" to senderName,
            "senderEmail" to senderEmail,
            "payload" to Base64.encodeToString(payload, Base64.NO_WRAP),
            "timestamp" to com.google.firebase.Timestamp.now(),
            "kind" to kind
        )
        if (msgId != null) {
            data["msgId"] = msgId
        }
        Log.d(TAG, "Sending with meta to $recipientEdPubHex (uid=$recipientUid, kind=$kind)")
        fireDb.collection(COLLECTION)
            .add(data)
            .addOnSuccessListener { Log.d(TAG, "Sent with meta successfully") }
            .addOnFailureListener { e -> Log.e(TAG, "Send with meta failed: ${e.message}", e) }
    }

    fun sendDeliveryAck(senderEdPubHex: String, msgId: String) {
        val fireDb = db ?: return
        fireDb.collection("delivery_acks").add(
            mapOf(
                "senderEdPub" to senderEdPubHex,
                "msgId" to msgId,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
        )
    }
}
