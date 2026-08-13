# Squelch — Build Status

> Living document. Updated at every milestone. Each section ends with the
> commit hash (or pending) and an exit-criterion.
>
> Last updated: **M10 + M-online shipped**. APK in
> `app/build/outputs/apk/debug/app-debug.apk`, 38.5 MiB.
> Tooling: JDK 21 Temurin · Gradle 8.14.3 · AGP 8.9.1 · Kotlin 2.1.20 ·
> compileSdk 36 / targetSdk 36 / minSdk 26 · app id `com.squelch.app`.

## Product shape

A serverless **peer-to-peer chat application over the internet** with an
**automatic Bluetooth + Wi-Fi Direct mesh fallback** when the network is
down. Identity & contacts live encrypted in the user's own Google Drive
so a stolen phone or a brand-new device restores with a PIN.

| Transport | Status | Stack |
|-----------|--------|-------|
| Online (always-on) | Spec'd, **deferred** for build #1 | `rust-libp2p` swarm, WebRTC/QUIC/Circuit Relay v2. No Rust crate yet; interface stubs only. |
| Offline (proximity) | **Building now** (Android) | Google Nearby Connections, `Strategy.P2P_CLUSTER`. Source-only scaffold for iOS via MultipeerConnectivity. |

## Identity portability pipeline

```
Device generates 24-word BIP-39 Mnemonic (256 bits entropy)
  → user picks 6-digit Master PIN
    → Google Sign-In → Drive scope drive.file
      → K_vault = Argon2id(PIN, salt = SHA256(Google UID))  (m=64MiB t=3 p=1)
        → encrypt {mnemonic_seed, contacts[], settings} with AES-256-GCM
          → upload to /squelch/vault.enc
```

Phone lost → user installs on the new phone → same Google account → enters PIN
→ vault download → decrypted → contacts identity restored.

## Threat model and the KDF choice

- **Stolen phone, attacker guesses PIN**: Argon2id makes offline brute force
  expensive.
- **Stolen phone, attacker removes Google account first**: Argon2id still
  gates vault.enc.
- **Lost Google account or one-time-password reset**: 24-word mnemonic acts
  as out-of-band backup; exportable in-app via Settings.

## Modules under `com.squelch.app/`

| Module | Role |
|--------|------|
| `auth/` | Google Sign-In wrapper, account state machine |
| `vault/` | BIP-39, Argon2id, AES-GCM, `DriveVaultManager` |
| `crypto/` | Wrappers over BouncyCastle |
| `db/` | Room entities + DAOs + SQLCipher factory |
| `mesh/` | `MeshEngine`, `MeshPacket`/`Envelope`, `OnlineLink` (libp2p stub), `OfflineLink` (Nearby) |
| `mesh/nearby/` | `AndroidMeshManager` lifted from spec §3.3 |
| `ui/` | Onboarding + main app |

## Identity portability in more detail

1. **BIP-39 Mnemonic**: 24-word wordlist, 256 bits entropy → 32-byte seed
   via PBKDF2-HMAC-SHA512 (2048 iters, "mnemonic" + passphrase).
2. **Master PIN**: 6 digits, validated twice on entry.
3. **Argon2id**: BouncyCastle `org.bouncycastle.crypto.generators.Argon2BytesGenerator`.
   Parameters: m=64 MiB, t=3, p=1. Output: 32 bytes → SHA-256(prk) for
   clean K_vault.
4. **Salt = SHA-256(Google UID)**: the UID (`sub` claim from
   `https://openidconnect.googleapis.com/v1/userinfo`) is stable per
   Google account. Without the same Google account, the salt is wrong
   and the vault can't decrypt.
5. **Drive folder**: `/squelch/`. Visible in the user's Drive web UI
   as a manual-backup affordance. Scope: `drive.file` (app-created
   files only).

## Local data layer

- **SQLCipher 4.5.4** opened with the vault-derived passphrase (`K_db =
  SHA-256(K_vault)` so K_db ≠ K_vault). Schemas: `identity`, `contacts`,
  `messages`, `mesh_queue`. Room provides DAOs; a custom
  `SupportSQLiteOpenHelper` factory wraps the SQLCipher `openOrCreateDatabase`
  call with the passphrase.
- No Android Keystore — the vault key is the source of truth.

## Offline mesh (Android)

`AndroidMeshManager` wraps `Nearby.getConnectionsClient(context)`.
- `SERVICE_ID = "com.squelch.p2p.mesh"`
- `Strategy.P2P_CLUSTER`
- `ConnectionLifecycleCallback.onConnectionInitiated` → `acceptConnection`
  with our `PayloadCallback` that hands raw bytes to `MeshEngine`.
- Foreground service hosts the `ConnectionsClient` lifetime.

## iOS scaffold (source-only)

- `ios/SquelchP2P/Sources/IOSMeshManager.swift` ← spec §4.2 lifted
- `ios/SquelchP2P/Info.plist` ← spec §4.1 strings + Bonjour services
- `ios/SquelchP2P/Package.swift` ← Swift Package manifest, openable in
  Xcode 14+
- No `.xcodeproj` on this Windows machine.

## UI/UX direction

- **Modern yet retro and mature "logged-in" feel.**
- Phosphor-green primary on deep matte black background (CRT terminal
  cue), amber accent (radar / attention). Monospaced type for status
  lines and headers — proportional for chat rows.
- Subtle CRT scan-line overlay on hero areas (low opacity, 1px stripe).
- No skeuomorphic nonsense: don't fake cardboard UIs. Pure clean
  flat, retro comes from colour, type, and CRT effect.
- Bottom navigation: **Chats**, **Contacts**, **Mesh**, **Settings**.
  Onboarding carousel: Sign In → PIN → Mnemonic backup confirm →
  Vault provisioned → enter app.
- Densified information display; status bars everywhere (SQUELCH v0.2.0
  + mesh status: links N, peers M, store-and-forward N).

## Logo: phosphor retro wave (locked)

- **Mark**: concentric oscilloscope rings + phosphor-green pulse dot,
  sitting in a rounded square. An implicit 'S' formed by the rings'
  horizontal axis. Subtle CRT scan-line mod on the foreground.
- **Palette**: background `#0A0A12`, ring + dot `#00FF41`, accent
  `#FFB000`. Gradients NOT used (keeps the mark printing well).
- **Sizes** generated:
  - `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`
    adaptive icon
  - `drawable/ic_launcher_foreground.xml` — the foreground vector
  - 512×512 PNG for marketing (App Store / Play Store icon,
    out of scope on Windows, regenerated later)

## What you need to give me (when convenient)

1. **OAuth 2.0 Android client ID** from Google Cloud Console
   (format `<digits>-<hash>.apps.googleusercontent.com`). Goes into
   `app/src/main/res/values/oauth_credentials.xml`, replacing the
   `REPLACE_WITH_YOUR_CLIENT_ID` placeholder.
2. **SHA-1 of your signing keystore** (from `./gradlew :app:signingReport`).
   Register it against the OAuth client in Google Cloud Console.
3. *(Optional)* A test **24-word mnemonic** for verifying vault round-trip
   once the vault layer is wired. The BIP-39 public test vector
   "abandon abandon abandon …" repeated 23 times + "about" works.

None of these block the scaffold; the OAuth flow will fail gracefully
until they're filled in.

## Milestone log

| # | Commit | Title | Status |
|---|--------|-------|--------|
| M0 | `cfabef1` | v1 archived: move v1 sources to `_archive/v1/`, swap root spec for v2 | ✅ pushed |
| M1 | `23c20f9` | v2 scaffold: gradle + Drive scopes + Sign-In deps + stub `MainActivity` + retro logo | ✅ pushed |
| M1 cleanup | `5e8ab27` | rename package com.squelch.app, register OAuth client id, app label 'Squelch' | ✅ pushed |
| M2 | `4448d8b` | Google Sign-In + Drive vault folder (REST, no Firebase) | ✅ pushed |
| M2 polish | `c653cf4` | in-app logo + shared Components.kt | ✅ pushed |
| M3 | `d75cec2` | BIP-39 + Argon2id + AES-256-GCM + on-device round-trip | ✅ pushed |
| M4 | `dc94a37` | SQLCipher-backed Room (3 entities + DAOs + Keyring) | ✅ pushed |
| M5 | `43f57e4` | offline mesh via Nearby Connections + foreground service | ✅ pushed |
| M6 | `6e88998` | PIN + mnemonic backup + vault unlock flow | ✅ pushed |
| M7 | `93ced3e` | chat surfaces + wire format + Noise XX sessions + payload routing | ✅ pushed |
| M8 | `97a2c46` | iOS source-only scaffold (MultipeerConnectivity + Swift Package) | ✅ pushed |
| M9 | `ce5f343` | CRT splash + marketing icon SVG + render script | ✅ pushed |
| M-online | `a112f5c` | libp2p-style online relay (WebSocket) + Settings + protocol spec | ✅ pushed |
| M10 | pending | AppShell -> Settings navigation + final APK + BUILD_STATUS update | in progress |

## M1 details (closed)

- **Files added**
  - `app/build.gradle.kts` (Google Sign-In 21.0.0, Drive v3, Nearby 19.3.0, SQLCipher 4.5.4, BouncyCastle 1.80, Room 2.7.1, Compose BOM 2025.05.00).
  - `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro`.
  - `app/src/main/res/values/strings.xml`, `themes.xml`, `colors.xml`.
  - `app/src/main/res/values/oauth_credentials.xml` (placeholders, will be filled when you drop real client ID).
  - `docs/oauth_credentials.example.xml` (reference copy).
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`.
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` (the **phosphor retro wave** mark), `ic_launcher_background.xml` (matte black with soft amber halo).
  - `app/src/main/java/com/squelch/p2p/SquelchApp.kt`, `MainActivity.kt`.
  - `BUILD_STATUS.md` (this document).
- **Fixes baked in**
  - Packaging excludes for the duplicate `META-INF/versions/9/OSGI-INF/MANIFEST.MF` from BouncyCastle and JSpecify.
  - Switched the launcher adaptive icon from `@color/squelch_background` to `@drawable/ic_launcher_background` so masked adaptive icons look correct on round devices.
- **Toolchain rehydration**
  - Re-extracted `gradle-8.14.3-bin.zip` and `temurin21.zip` from `C:\Users\roysa\AppData\Local\Temp\opencode\` (the unpacked dirs had been wiped mid-session).
  - Updated `gradle.properties org.gradle.java.home` → `C:/Users/roysa/AppData/Local/Temp/opencode/jdk/jdk-21.0.12+8`.
- **Build result**: `BUILD SUCCESSFUL in 41s, 36 actionable tasks: 13 executed, 23 up-to-date`.

## Decisions made during M1

1. **Folder name `/squelch/`** (locked earlier). AppFolders root visible in Drive web UI; user can download `vault.enc` manually as cold-storage backup.
2. **Logo: phosphor retro wave.** Concrete files committed; PNG export for Play Store / App Store deferred to M9 (needs non-Windows tooling).
3. **No Firebase**. `google-services` plugin is **NOT** applied; `google-services.json` is **NOT** generated. The app uses `GoogleSignInClient` (play-services-auth) and the Drive v3 client (google-api-services-drive) directly.
4. **OAuth credentials placeholder**. The XML ships with `REPLACE_WITH_YOUR_CLIENT_ID.apps.googleusercontent.com` so the build doesn't fail; replacing it needs:
   - The OAuth client ID from Google Cloud Console (you).
   - The SHA-1 fingerprint of your signing keystore, registered against that OAuth client (you, via console).

## Next 3 actions (you do, then I resume)

| # | Action | You? | Me? |
|---|--------|------|------|
| 1 | Run `git status` and review the M1 staged diff (`git diff --stat`). | you | — |
| 2 | Tell me to **commit + push** M1, or to **hold** for edits. | you | — |
| 3 | (Parallel, optionally) Drop real OAuth client ID + SHA-1 + folder name into `oauth_credentials.xml` so M2 isn't on placeholder. | you | — |

Once you say "commit + push", I'll commit M1 and immediately start M2 in a new worktree. Until then I won't push anything.



## Build commands

```bash
& "C:\Users\roysa\AppData\Local\Temp\opencode\gradle\dist\gradle-8.14.3\bin\gradle.bat" assembleDebug --console=plain
```

Toolchain cache locations:

- Gradle 8.14.3 → `C:\Users\roysa\AppData\Local\Temp\opencode\gradle\dist\gradle-8.14.3\bin\gradle.bat`
- JDK 21 Temurin → `C:\Users\roysa\AppData\Local\Temp\opencode\jdk\jdk-21.0.12+8\` (wired via `gradle.properties org.gradle.java.home`)
- Android SDK → `C:\Users\roysa\AppData\Local\Android\Sdk` (wired via `local.properties`)

## Known environment quirks

- Installed Android SDK platform jars are **stripped** of some low-usage
  APIs (e.g. `BluetoothAdapter.openGattServer` was missing on android-36).
  v1 worked around it via reflection. v2 should not depend on those APIs
  because Nearby Connections abstracts BLE away.
- `libsqlcipher.so` and `libandroidx.graphics.path.so` could not be
  stripped at packaging on Windows — they are shipped unstripped in the
  debug APK. This is harmless for development and expected on Windows.
- Keep tools in `C:\Users\roysa\AppData\Local\Temp\opencode\` — that
  directory is wiped between sessions; the ZIPs survive longer than the
  extracts.
