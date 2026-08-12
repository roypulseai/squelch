# _archive/v1/

This directory contains the **v1** Android implementation of Squelch that
predates the v2 spec at `../squelch-spec.md`. It is preserved here as a
historical reference; it is not part of the v2 build and is not built by
the root `settings.gradle.kts`.

## Layout

- `spec.md` — the v1 spec (Radar/retro UI, Ed25519 seed identity,
  Noise XX E2E, custom BLE+Wi-Fi Direct transport, HCE NFC pairing).
- `android-v1/app/` — the v1 Kotlin Android module. This module had its
  own `build.gradle.kts`, used package `com.squelch.app`, and was
  ~80% complete against the v1 spec at the time it was archived
  (1 outstanding compile error in `X25519.kt:32`).

## v1 milestones that shipped before archive

- Crypto: Ed25519, X25519, Identity (Keystore-backed), Noise XX/KK
  handshake state machine, AES-GCM room keys.
- Wire: `MeshPacket` (signed v1 payload), `MeshEnvelope` (DM/ROOM/HS
  kinds), `LinkCodec` chunk framing, `Hello` (`SQH1` TLV), `Peer` with
  disambiguated callsign, `StoreAndForward` cache, room manager.
- Transports: `BleTransport` (GATT server+client with pubkey
  tie-break), `WifiDirectTransport` (`KIND_WIFI_OFFER`), HCE NDEF
  service + `NfcTapManager` (ISO-DEP).
- App layer: `SquelchApp`, `MeshService` (foreground connectedDevice),
  `MainActivity`, retro Compose UI (Radar/Chats/Rooms/Contacts/Settings).

## Architecture changes in v2 (relevant to anyone porting v1 code)

1. Identity is no longer derived locally from an Ed25519 seed. v2 uses a
   24-word BIP-39 mnemonic stored encrypted (Argon2id+ AES-256-GCM) in
   `/squelch/vault.enc` on the user's personal Google Drive.
2. Custom BLE GATT is replaced by Google Nearby Connections
   (`Strategy.P2P_CLUSTER`) on Android and MultipeerConnectivity on iOS.
   `openGattServer` and tie-break logic are gone.
3. HCE NFC pairing is removed (no NFC in v2 spec).
4. Local Room DB is replaced by SQLCipher (still accessed via Room, but
   with a custom `SupportSQLiteOpenHelper` that opens the SQLite
   database with the user's vault-derived passphrase).

## To view v1 history

```bash
git log -- '_archive/v1/**'
```

The v1 module is gitignored at the root level so it does not affect
v2 builds. To re-enable building v1 for archaeology, temporarily move
`_archive/v1/android-v1/app/` back to `app/` and adjust the namespace.
