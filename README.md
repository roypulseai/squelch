# Squelch

**Private, peer-to-peer encrypted messaging on Android — no servers, no phone numbers, no ads.**

Squelch is a privacy-first chat app that derives your identity from your Google account. Messages are relayed through Firestore but travel as encrypted payloads. Your vault (identity, contacts, settings) is encrypted and stored in Firestore, locked behind biometric authentication.

---

## Features

- **Sign in with Google** — no phone number, no account creation. Your identity is cryptographically derived from your Google UID.
- **E2E encrypted vault** — contacts, keys, and settings are encrypted with AES-256-GCM and stored in Firestore.
- **Biometric lock** — app auto-locks when backgrounded, requires fingerprint/PIN on resume.
- **1:1 and group chat** — message your contacts or create groups with admin/member roles.
- **@mention autocomplete** — type `@` in group chats to mention members.
- **Message actions** — long-press to copy, edit, recall (unsend), or forward messages.
- **Recall & edit** — edits and recalls sync to the other device in real-time.
- **QR code contact exchange** — scan a friend's QR to add them instantly.
- **Find by email** — search for users by their Google email.
- **Radar** — visualize nearby peers and transport status (Internet, BLE, Wi-Fi Direct).
- **Push notifications** — FCM-powered notifications for new messages.
- **Stranger messages** — unknown contacts appear in a separate filtered inbox with add/block options.
- **Mute & delete** — mute notifications per conversation or delete chat history.

---

## Architecture

```
┌────────────────────────── Squelch ──────────────────────────┐
│  Compose UI    ChatsScreen  ConversationScreen  RadarScreen │
│                ContactsScreen  SettingsScreen               │
├─────────────────────────────────────────────────────────────┤
│  Domain        ChatViewModel  MessageRelay  VaultRepository │
│                MeshEngine  SessionManager                    │
├─────────────────────────────────────────────────────────────┤
│  Transport     FirestoreTransport (Firestore messages)      │
│                BleTransport (BLE mesh, planned)              │
│                WifiDirectManager (Wi-Fi Direct, planned)     │
├─────────────────────────────────────────────────────────────┤
│  Crypto        Ed25519 / X25519 identity (SHA-512 derived)  │
│                X25519 ECDH + AES-256-GCM (E2ECrypto)        │
│                Noise Protocol XX handshake (MeshEngine)      │
├─────────────────────────────────────────────────────────────┤
│  Storage       SQLCipher + Room (messages, contacts, groups) │
│                Firestore (vault, message relay, user profiles)│
│                AndroidKeyStore (biometric-locked vault key)   │
└─────────────────────────────────────────────────────────────┘
```

### Identity

```
identity = SHA-512("squelch_identity_v1:$googleUid")
  → ed25519 public key (signing)
  → x25519 public key (encryption)
```

No mnemonic, no recovery phrase. Your Google account **is** your identity.

### Vault

```
vaultKey = SHA-256("squelch_vault_v1:$googleUid)
vault = AES-256-GCM(vaultKey, {contacts, keys, settings})
```

Stored in Firestore at `vaults/{googleUid}`. Optional biometric lock encrypts `vaultKey` with an AndroidKeyStore key requiring user authentication.

### Message Relay

Messages flow: **Sender → Room DB + Firestore → Recipient's listener → Room DB → UI**

Firestore `messages` collection acts as a transient relay. Documents are deleted after being consumed. The relay carries metadata (sender name, email, kind) for proper message handling.

### Transport Kinds

| Kind | Value | Purpose |
|------|-------|---------|
| `KIND_DATA` | 2 | Normal message |
| `KIND_RECALL` | 4 | Recall (unsend) a message |
| `KIND_EDIT` | 5 | Edit a sent message |

---

## Build

### Requirements

- JDK 21
- Gradle 8.14.3
- Android SDK 36
- Firebase project with Firestore enabled

### Setup

```bash
# Clone
git clone https://github.com/roypulseai/squelch.git
cd squelch

# Place google-services.json in app/
# Place signing keystore at project root as squelch.jks

# Build debug APK
gradle :app:assembleDebug
```

---

## Repository Layout

```
squelch/
├── app/
│   ├── src/main/java/com/squelch/app/
│   │   ├── auth/           Google sign-in, biometric manager
│   │   ├── crypto/         Identity, E2ECrypto, VaultCipher
│   │   ├── data/
│   │   │   ├── local/      Room DB, DAOs, entities, migrations
│   │   │   ├── remote/     Firestore vault, Drive backup
│   │   │   └── repository/ VaultRepository
│   │   ├── messaging/      FCM service, token manager, foreground service
│   │   ├── mesh/
│   │   │   ├── relay/      MessageRelay (Firestore ↔ Room bridge)
│   │   │   ├── transport/  FirestoreTransport, BleTransport
│   │   │   ├── engine/     MeshEngine (Noise Protocol)
│   │   │   └── session/    SessionManager
│   │   └── ui/
│   │       ├── navigation/ AppEntry, Screen routes
│   │       ├── screens/    Chats, Conversation, Contacts, Radar, Settings, Onboarding
│   │       └── theme/      Material3 theme
│   └── build.gradle.kts
├── functions/               Cloud Functions (dormant)
├── firebase.json            Firebase config
└── README.md
```

---

## License

MIT

```
Squelch
Copyright (c) 2026 Roypulse (roypulseai)
```

---

## Reporting Issues

Issues: <https://github.com/roypulseai/squelch/issues>
Security: <roypulse.ai@gmail.com>
