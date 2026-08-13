# Squelch P2P

**Serverless, peer-to-peer chat on your phone — Google Drive keeps your identity, nearby phones keep your conversations.**

Squelch is an Android-first, serverless P2P chat app that runs over:

- **Online P2P** (with a relay fallback so chat works even behind firewalls),
- **Nearby Connections** when there's no internet — BLE + Wi-Fi Direct,
- **Encrypted vault** for identity, contacts and settings, stored in your own
  Google Drive.

There is **no Squelch server** in the data path. The relay is just a small
forwarder for offline peers; the bytes that move through it are Noise-encrypted
end-to-end. There is no analytics, no telemetry, no account creation, and no
phone number. Sign in with Google → set a 6-digit PIN → write down a 24-word
recovery phrase → you're in.

---

## Contents

- [User guide](#user-guide)
  - [Install + sign in](#install--sign-in)
  - [Set up your vault (first launch)](#set-up-your-vault-first-launch)
  - [Add a contact](#add-a-contact)
  - [Start a chat](#start-a-chat)
  - [Settings (change PIN, lock, export identity)](#settings-change-pin-lock-export-identity)
  - [Recover on a new phone](#recover-on-a-new-phone)
- [Architecture](#architecture)
  - [Identity + Drive vault](#identity--drive-vault)
  - [Nearby mesh](#nearby-mesh)
  - [Online relay](#online-relay)
- [Build](#build)
- [Repository layout](#repository-layout)
- [License](#license)

---

## User guide

### Install + sign in

```bash
# build the debug APK
tools/release-build.sh apk
# install on a device
adb install app/build/outputs/apk/release/app-release.apk
```

Open Squelch. The first screen says **SIGN IN WITH GOOGLE**. Tap it,
pick the Google account you want to use, accept the **drive.file** scope.

> Squelch only ever asks for `drive.file` — a Google-scoped permission
> that lets it read/write files it itself created under your Drive. It
> cannot see anything else in your Drive. Squelch never sends plaintext
> chat bytes anywhere; the only thing in /squelch/ is the encrypted
> identity vault.

### Set up your vault (first launch)

After signing in you'll be asked for a **6-digit PIN** and shown a
**24-word BIP-39 recovery phrase**. Both are required.

| Step | What happens | Where |
|---|---|---|
| 1 | Squelch generates a fresh 24-word BIP-39 mnemonic (256 bits entropy) on-device. | nowhere |
| 2 | You write the 24 words down on paper and confirm with two checkboxes. | your paper |
| 3 | You choose a 6-digit PIN. | your head |
| 4 | Squelch derives `K_vault = SHA-256(Argon2id(PIN, salt=SHA-256(GoogleUID)))`. | on-device |
| 5 | Squelch encrypts `{mnemonic_seed, contacts, settings}` with AES-256-GCM using `K_vault` and AAD `"squ|vault|v1"`. | on-device |
| 6 | Squelch uploads the encrypted blob as `vault.enc` to your `/squelch/` Drive folder. | your Drive |
| 7 | `K_db = SHA-256(K_vault)` opens the local SQLCipher database (`squelch.db`). | on-device |

The PIN is **never** stored anywhere. Lose it and the vault is
unrecoverable. The 24-word phrase **is** the recovery path.

### Add a contact

There are three ways to add a contact:

| Way | How |
|---|---|
| **Auto via mesh** | Once you START MESH, anyone nearby who's also signed in will appear under **PEERS** automatically after the handshake completes. Tap them to open a chat. |
| **QR-code exchange** | Tap **MY QR** to display your contact QR. A peer scans it with **ADD CONTACT → SCAN QR**. |
| **Manual** | **ADD CONTACT → PASTE** and paste an exported contact JSON blob. |

A contact record is `{edPub (Ed25519), xPub (X25519), callsign, trustLevel, lastSeen, capabilities}`.

### Start a chat

- **PEERS** lists currently linked devices.
- Tap a peer → chat panel with a `> msg...` field. Tap **SEND** to push the
  message through Noise XX on the active transport (Nearby if in range,
  else the online relay).
- The first message on a new link performs the Noise XX handshake. The
  session is encrypted and authenticated end-to-end; the relay sees only
  ciphertext bytes.

### Settings (change PIN, lock, export identity)

Tap **SETTINGS** in the main app bar to land on:

- **ONLINE RELAY** — connection state for the WebSocket relay.
- **VAULT** — local database fingerprint.
- **CHANGE PIN** — rotate your PIN. Old PIN is verified, new PIN is asked,
  the vault is re-encrypted with the new K_vault, the Drive blob is
  overwritten, and the local DB is re-keyed.
- **EXPORT IDENTITY** — produces a base64 blob of your mnemonic_seed
  (32 bytes). Save it on a USB stick, write it on paper again, whatever.
- **LOCK NOW** — wipes K_db + K_vault from memory and closes the SQLCipher
  database.
- **SIGN OUT** — clears the Google Sign-In session.

### Recover on a new phone

1. Install Squelch. Sign in with the **same Google account** that owns
   the original `/squelch/` Drive folder.
2. Tap **UNLOCK**. Squelch sees an existing `vault.enc`, prompts for the
   PIN, decrypts, and re-derives `K_db` to open the local database.
3. If you don't remember the PIN, you can paste the **24-word mnemonic**
   instead. Squelch will rebuild `K_vault` from the mnemonic + the new
   device's Google UID (which **will** fail unless the Google UID
   matches the original), or it offers **RESET FROM MNEMONIC** which
   generates a new PIN and re-encrypts the vault.

> ⚠️ Cross-account restore is **not** possible because the salt
> `SHA-256(GoogleUID)` is keyed to the original account. This is by
> design — the vault is bound to the Google account that produced it.

---

## Architecture

```
┌────────────────────────────── Squelch P2P ──────────────────────────────┐
│  Compose UI         ←──── AuthState + VaultFlowState + MeshStatus        │
│  AppShell              OnboardingViewModel  SignInScreen  PinEntryScreen  │
│  MessagesScreen      ChatsScreen       RadarScreen     SettingsScreen   │
├────────────────────────────────────────────────────────────────────────┤
│  Domain layer    MessageLayer  RoomManager  SessionManager(Noise XX)    │
│  Wire format     MeshPacket  MeshEnvelope  InnerMessage  LinkCodec      │
├────────────────────────────────────────────────────────────────────────┤
│  Transports      AndroidMeshManager (Nearby P2P_CLUSTER, BLE+Wi-Fi)    │
│                   WebSocketRelayLink (online via OkHttp)                │
│                   (future) rust-libp2p swarm — byte-compatible shim      │
├────────────────────────────────────────────────────────────────────────┤
│  Crypto            Argon2id (BC)   AES-256-GCM (JCE)   Ed25519/X25519   │
│                    BIP-39 (wordlist)   Noise XX   VaultCipher           │
├────────────────────────────────────────────────────────────────────────┤
│  Storage          SQLCipher-Room (contacts, chats, rooms, mesh queue)   │
│                   Google Drive vault.enc (encrypted identity blob)      │
└────────────────────────────────────────────────────────────────────────┘
```

### Identity + Drive vault

```
mnemonic_seed  = BIP-39(mnemonic)         # 32 bytes from 24-word phrase
K_vault        = SHA-256(Argon2id(PIN, salt=SHA-256(GoogleUID), m=64MiB t=3 p=1))
K_db           = SHA-256(K_vault)
vault.enc     = AES-256-GCM(K_vault, payload, aad="squ|vault|v1")
                upload to /squelch/vault.enc
```

### Nearby mesh

Uses `com.google.android.gms.nearby.Nearby` with
`Strategy.P2P_CLUSTER`. One device is the GATT server, the other is the
client; tie-break: **the peer with the larger Ed25519 pubkey connects as
client**. Bytes flow peer-to-peer over Wi-Fi Direct (preferred) or BLE.

### Online relay

`docs/relay-protocol.md` defines a 100-line JSON-over-WebSocket protocol
that lets two clients exchange ciphertext bytes when neither Nearby
nor direct NAT-punch is available. The relay authenticates clients
with the same Google OAuth bearer used for Drive and binds each
EdPub to the corresponding Google UID.

A reference Go server is included in the protocol doc; production
deployment is `wss://relay.squelch.app/v2/mesh`.

---

## Build

### Toolchain

- JDK 21 (Temurin)
- Gradle 8.14.3
- Android SDK 36
- NDK 26 for SQLCipher native libs

### Setup

```bash
# 1. JDK + Gradle + Android SDK are unpacked under
#    C:/Users/roysa/AppData/Local/Temp/opencode/ - extract the
#    .zip bundles in that directory if the build complains about a
#    missing JDK / Gradle / SDK.

# 2. local.properties needs:
#      sdk.dir=C:/Users/roysa/AppData/Local/Android/Sdk
#      squelch.keystore.file=keystore/squelch.jks
#      squelch.keystore.storepass=<password>
#      squelch.keystore.keypass=<password>
#      squelch.keystore.alias=squelch

# 3. Release keystore (one-time per machine):
tools/setup-release-keystore.sh
#    SHA-1 fingerprint printed at the end is the one to register
#    against your Google Cloud OAuth client.

# 4. Build:
& gradle assembleDebug             # 38 MiB APK in app/build/outputs/apk/debug/
tools/release-build.sh apk        # signed APK
tools/release-build.sh bundle     # AAB for Play Store
tools/upload-to-play.sh bundle/release/app-release.aab internal
```

### Variant OAuth clients (recommended for production)

The default setup uses **one** OAuth client with **one** SHA-1 (the
release keystore). For long-term maintenance you'll want two OAuth
clients (debug + release) and `oauth_credentials.xml` will pick the
right one via `BuildConfig.DEBUG`. See
`res/values/oauth_credentials.xml` for the comment describing the
two-client flow.

---

## Repository layout

```
squelch/
├─ app/                       Android Studio project
├─ ios/                       Source-only iOS scaffold (open in Xcode)
├─ marketing/                 icon.svg + render-marketing-icons.sh
├─ tools/                     setup-release-keystore.sh, release-build.sh,
│                             upload-to-play.sh, oauth-debug-info.sh
├─ docs/                      relay-protocol.md, repo-settings.md
├─ _archive/                  v1 archived source + spec
├─ squelch-spec.md            v2 spec (the source of truth)
├─ BUILD_STATUS.md            live build milestone log
├─ keystore/                  squelch.jks (gitignored) + example placeholder
├─ settings.gradle.kts        gradle module layout
├─ build.gradle.kts           root gradle config
└─ README.md
```

## License

MIT.

```
Squelch P2P
Copyright (c) 2026 Roypulse (roypulseai)
```

No warranty. See LICENSE for the full text.

---

## Reporting issues

Issues live on GitHub at <https://github.com/roypulseai/squelch/issues>.
For security disclosures (vulnerabilities, hash collisions, protocol
weaknesses) email <roypulse.ai@gmail.com>.