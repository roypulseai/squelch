# Squelch P2P — iOS scaffold

Source-only. Open in Xcode 14+ on macOS to build a working iPhone app.
Cannot be compiled on Windows.

## Files

```
ios/
├─ Package.swift                  # Swift Package manifest
├─ docs/
└─ SquelchP2P/
   ├─ Resources/
   │  └─ Info.plist               # Bluetooth / Local Network / Bonjour entries
   ├─ Sources/
   │  ├─ IOSMeshManager.swift     # MultipeerConnectivity analog to the Android
   │  │                           # AndroidMeshManager. Same wire bytes on both
   │  │                           # sides, so a Squelch phone running the
   │  │                           # iOS app can chat with one running the
   │  │                           # Android app on the same Wi-Fi / BLE.
   │  └─ Crypto/
   │     └─ BIP39.swift           # Stub for BIP-39 generation. Real
   │                               # implementations swap to CryptoKit or
   │                               # libsodium in production builds.
```

## What works in this scaffold

- `IOSMeshManager` advertises + browses under the Bonjour service
  `squelch-mesh`, mirroring the Android `AndroidMeshManager`.
- Per-peer `MCSession` auto-accepts invitations.
- The two frame-kind bytes (0x01 HELLO, 0x02 DATA, 0x03 HANDSHAKE) and
  the MeshPacket / MeshEnvelope wire formats are identical to the
  Kotlin side, so an Android peer and an iOS peer can interoperate as
  soon as the BouncyCastle Noise / Argon2id stack is replaced with
  Apple's CryptoKit (the protocol surface stays the same).

## What is NOT in this scaffold

- A fully-fledged Noise XX / Argon2id implementation - the scaffold
  ships a no-op `BIP39.generateMnemonic` and a `HexBytes` helper.
  Real builds should swap these for a CryptoKit-based module
  mirroring the Android `Bip39` / `VaultCipher` / `Argon2id` design.
- Vault upload/download via Google Drive is Android-only on v0.7
  (Google Sign-In + Drive SDK + Room over SQLCipher). The iOS client
  should consume the same vault file format but is out of scope for
  this M8 milestone.
- The Xcode `.xcodeproj` / `Info.plist` bundle assets. This directory
  contains a `Package.swift` and an `Info.plist` resource; copy the
  resources into your target once you wire it into an app.

## Build (on macOS, with Xcode 14+)

```
open Package.swift    # or `swift build`
```

The `IOSMeshManager` requires link-time access to the
`MultipeerConnectivity` framework, which is available in the iOS 14+
SDK.

## Test on device

1. Run the package on two iOS devices signed into the same Wi-Fi network
   (multipeerconnectivity auto-selects BLE/infra). They should discover
   each other automatically once both have called `manager.start()`.
2. Tap "share identity" on one; the other receives the hello frame
   containing the peer's edPub + xPub + call-sign.
3. Handshake (Noise XX) and chat follow the same envelope formats as
   Android - bytes cross the wire verbatim.
