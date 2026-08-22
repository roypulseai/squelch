# Squelch — Project Specification & Progress

## Overview

Squelch is an Android P2P encrypted chat app combining internet-based messaging (Firestore relay) with offline mesh networking (BLE + WiFi Direct). Users sign in with Google, derive cryptographic identity from their Firebase UID, and can chat over both internet and local mesh.

**GitHub:** `https://github.com/roypulseai/squelch.git`
**Local:** `D:\Mesh Project\squelch`
**Version:** 1.1.0 (versionCode 4)

---

## Tech Stack

- **Language:** Kotlin 2.1.20
- **Build:** Gradle 8.14.3, AGP 8.9.1
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt 2.56.2 (KAPT)
- **DB:** Room v5 + SQLCipher (encrypted SQLite)
- **Backend:** Firebase (Auth, Firestore, Cloud Messaging)
- **Crypto:** BouncyCastle (Ed25519, X25519, AES-GCM, Noise Protocol)
- **Transport:** Firestore (internet), BLE GATT (mesh), WiFi Direct (mesh)
- **Min SDK:** 26 (Android 8.0), **Target SDK:** 36

---

## Architecture

### Navigation Flow
```
SignIn -> Permissions -> [Restore?] -> Chats (main)
                                      +-- Contacts
                                      +-- Radar (mesh)
                                      +-- Settings
```

### Key Components

#### Authentication (`auth/`)
- `FirebaseAuthManager` — Google Sign-In + Firebase credential exchange
- `AuthRepository` — Single source of truth for auth state
- `BiometricManager` — BiometricPrompt wrapper
- `BiometricVaultManager` — Stores/encrypts vault key with AndroidKeyStore

#### Crypto (`crypto/`)
- `Identity` — Ed25519 + X25519 keys derived from `SHA-512("squelch_identity_v1:$googleUid")`
- `VaultCipher` — AES-GCM vault encryption
- `VaultSession` — In-memory unlocked state (zeroed on lock)
- `E2ECrypto` — ECIES-style message encryption (X25519 DH + AES-GCM)
- `noise/` — Noise Protocol Framework (XX pattern, auto-rekey every 1000 msgs)

#### Database (`data/local/`)
- **Version:** 5 with SQLCipher
- **Entities:** ContactEntity, MessageEntity, ConversationEntity, SettingEntity, GroupEntity, GroupMemberEntity, BlockedEntity
- **Migrations:** v1->v2 (firebaseUid), v2->v3 (groups, blocked), v3->v4 (role), v4->v5 (email)
- `fallbackToDestructiveMigration` enabled as safety net

#### Transport (`mesh/transport/`)
- `Transport` interface — start/stop/send/incoming
- `TransportFrame` — senderEdPubHex, kind, payload, senderName, senderEmail, msgId
- **Frame kinds:** HELLO=1, DATA=2, HS=3, RECALL=4, EDIT=5, BLOCKED=6, UNBLOCKED=7
- `FirestoreTransport` — Internet relay (ephemeral docs, deleted on read)
- `BleTransport` — BLE GATT server/client for mesh
- `WifiDirectTransport` — WiFi Direct socket-based transport

#### Mesh Engine (`mesh/engine/`)
- `MeshEngine` — Noise sessions per peer, fans frames across all transports
- `MeshEngineManager` — Singleton, creates engine on demand, collects incoming mesh messages -> Room DB
- `MessageCodec` — JSON wire codec

#### Message Relay (`mesh/relay/`)
- `MessageRelay` — Primary cloud-relay orchestrator
  - E2E encrypts payloads with E2ECrypto before writing to Firestore
  - Handles commands: recall, edit, block, unblock, delivery ack
  - TTL cleanup: deletes Firestore docs older than 24h
  - Shows notifications on incoming messages

#### Translation (`translate/`)
- `TranslationManager` — On-device ML Kit translation
  - Language detection + translation via Google ML Kit (offline)
  - Supports 30+ languages
  - Per-message translation with caching
  - Toggle in conversation top bar + language selector in Settings
  - Original text shown below translation when available

#### Messaging Services (`messaging/`)
- `SquelchMessagingService` — FCM push receiver
- `MessageForegroundService` — Sticky foreground service, 30-sec Firestore poll
- `MessagePollWorker` — WorkManager periodic backup poll (15 min)
- `FcmTokenManager` — Stores FCM token in Firestore

#### Cloud Sync (`data/remote/`)
- `FirestoreVaultManager` — Vault upload/download (GZIP compressed)
- `ContactSyncManager` — Contacts push/pull as compressed JSON
- `DriveBackupManager` — Google Drive backup/restore via REST API

---

## Message Flow

### Internet (Firestore)
```
Sender: ChatViewModel.sendMessage()
  -> Room DB insert (direction=1, delivery=0)
  -> MessageRelay.sendMessage()
    -> E2ECrypto.encryptFor() [X25519 DH + AES-GCM]
    -> FirestoreTransport.sendWithMeta() -> Firestore "messages" collection
  -> MeshEngine.sendMessage() [also sends via BLE/WiFi if peers connected]

Receiver: FirestoreTransport snapshot listener
  -> doc detected -> emit TransportFrame -> delete doc
  -> MessageRelay.handleIncoming()
    -> E2ECrypto.decryptWithMyKey()
    -> Parse JSON commands (recall/edit/block/unblock/ack)
    -> Store MessageEntity in Room DB
    -> Send delivery ack back to sender
    -> Show notification (sound + vibration)
```

### Mesh (BLE)
```
Sender: MeshEngine.sendMessage()
  -> Noise session encrypt (if established) or plaintext handshake init
  -> MessageCodec encode -> JSON wire format
  -> BleTransport.send() -> GATT write to peer's MSG characteristic

Receiver: GATT server onCharacteristicWriteRequest
  -> BleTransport._incoming.emit(TransportFrame)
  -> MeshEngine.handleFrame()
    -> Noise session decrypt (if KIND_DATA)
    -> MessageCodec decode -> emit IncomingMessage
  -> MeshEngineManager.collectMeshMessages()
    -> Store in Room DB -> show notification
```

---

## E2E Encryption

- **Internet:** E2ECrypto envelope (X25519 DH + AES-GCM)
- **Mesh:** Noise Protocol XX pattern (forward secrecy, auto-rekey)
- **Vault:** AES-GCM with key from SHA-256
- **Firestore messages are ephemeral** — deleted after recipient reads

---

## Features

### Working
- Google Sign-In with account picker
- E2E encrypted chat over internet (Firestore relay)
- Instant notifications with sound + vibration
- Foreground service "Monitoring for messages"
- Contact sync (Firestore push/pull)
- QR code scanning (orientation-free)
- Find contacts by Gmail
- Delivery ticks (sent/delivered/read)
- Message recall + edit (synced across devices)
- Forward messages
- Block/unblock users with notification
- Group chat with admin roles
- Pin/mute/delete conversations
- Delete contacts
- Biometric vault lock (auto-lock on background)
- Google Drive backup/restore
- Firestore TTL cleanup (24h)
- Noise handshake JSON filtering (no garbage in chat)
- Permissions management from Settings
- Squelch user badge on Radar

### In Progress / Needs Work
- BLE mesh messaging (peers discovered but messages not reliably delivered)
- WiFi Direct transport (discovery works, socket delivery needs testing)
- Multi-hop relay (not implemented — bitchat uses 7-hop TTL flood)
- Message fragmentation for large payloads over BLE
- Store-and-forward for offline peers

---

## Bitchat Learnings (Reference Architecture)

Studied `permissionlesstech/bitchat-android` for BLE mesh design. Key takeaways:

### Bitchat BLE Architecture
1. **Dual role:** Every device is simultaneously GATT Central (scanner) + GATT Peripheral (advertiser)
2. **GATT UUIDs:** Custom service UUID `F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C`, characteristic `A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D`
3. **Advertising:** Service UUID in advertisement data, 8-byte peer ID in scan response service data
4. **MTU negotiation:** Requests 517 (BLE max) before service discovery
5. **Connection lifecycle:** scan -> connect -> MTU -> discoverServices -> enable notifications -> ANNOUNCE packet
6. **Packet format:** Binary `BitchatPacket` with version, type, senderID, recipientID, timestamp, payload, signature, TTL
7. **Message relay:** Controlled flood with TTL=7 hops, adaptive probability based on network size
8. **Fragmentation:** Split at 469 bytes (under 512 MTU), reassemble with fragment ID + index
9. **Duplicate detection:** Seen-set with bounded LRU, capacity 500-10000
10. **Noise handshake:** XX pattern, initiated on first private message
11. **Power management:** Adaptive duty cycling (scan on/off windows), battery-aware
12. **Self-healing:** Scan watchdog, advertising retry with backoff
13. **Store-and-forward:** Cache messages for offline peers, deliver on reconnect

### What Squelch Should Adopt
- Binary packet format instead of JSON (smaller, faster)
- TTL-based flood relay for multi-hop mesh
- Fragmentation for messages > MTU
- Connection limit management (max 8 concurrent)
- Power-aware scanning (duty cycling)
- Self-healing scan/advertise recovery
- Store-and-forward for offline peers

---

## Permissions

| Permission | API Level | Purpose |
|-----------|-----------|---------|
| INTERNET | All | Firestore relay |
| BLUETOOTH_SCAN | 31+ | BLE peer discovery |
| BLUETOOTH_ADVERTISE | 31+ | BLE advertising |
| BLUETOOTH_CONNECT | 31+ | GATT connections |
| ACCESS_FINE_LOCATION | <=30 | BLE scan requirement |
| NEARBY_WIFI_DEVICES | 33+ | WiFi Direct |
| POST_NOTIFICATIONS | 33+ | Message notifications |
| FOREGROUND_SERVICE_DATA_SYNC | All | Message polling service |

---

## Recent Commits

| Commit | Description |
|--------|-------------|
| `1dfb6d8` | Rewrite BLE transport: GATT write queue, fragmentation, connection limits, self-healing, store-and-forward |
| `ee52a72` | Add in-chat translation with ML Kit (WeChat-style toggle, language selector, on-device) |
| `6aaab91` | Add to Contact from stranger chat, contact sync button, group creates conversation, fix getMemberName |
| `eb031f1` | Polish Chats/Contacts UI, add pin/mute/delete, backup descriptions |
| `e21809d` | Fix crash on sign-in: vault decompression fallback, destructive migration |
| `ad8d570` | Fix app freeze: remove runBlocking, Thread.sleep, fix peer observation |
| `bc5a920` | Fix block notification in handleIncoming to use JSON cmd format |
| `8f91bb9` | E2E encrypt messages in Firestore, TTL cleanup, fix block/unblock JSON |
| `f7dcd14` | Fix broken chat, Noise JSON leak, permissions skip, radar, notifications |

---

## Known Issues

1. **BLE mesh:** Store-and-forward is single-hop only — needs multi-hop TTL relay for full mesh
2. **WiFi Direct:** Socket connection needs real-device testing (BlueStacks doesn't support WiFi Direct)
3. **No multi-hop relay:** Messages only reach directly connected peers
4. **MainScope() leaks:** Several places use MainScope().launch instead of rememberCoroutineScope
5. **No offline message queue:** Messages sent while offline are lost (store-and-forward caches on BLE only)
6. **Vault security:** Key derived from non-secret googleUid (known limitation, acceptable for v1)

---

## Next Steps

1. **Implement multi-hop relay:**
   - TTL-based flood (7 hops max)
   - Duplicate detection (seen-set with LRU) — done in BLE transport
   - Adaptive relay probability

2. **Fix remaining UI issues:**
   - Replace MainScope with rememberCoroutineScope
   - Add message length validation
   - Preserve state across rotation (rememberSaveable)

3. **Security hardening:**
   - Sign E2ECrypto envelopes with Ed25519
   - Add Argon2id to vault key derivation
