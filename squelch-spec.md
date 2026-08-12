# Squelch: Mobile P2P Chat Application Architecture & Developer Specification

**Version:** 2.0.0  
**Target Platforms:** Android (Kotlin) & iOS (Swift) / Cross-Platform (Flutter/React Native)  
**Application Name:** Squelch  
**Core Stack:** Firebase Auth, Google Drive API v3, `rust-libp2p`, Google Nearby Connections (Android), Multipeer Connectivity (iOS), SQLCipher.

---

## 1. System Overview & Product Vision

**Squelch** is a serverless, peer-to-peer (P2P) messaging application for mobile devices. It operates without central message brokers, user databases, or phone number requirements.

### Core Architecture Capabilities
* **Identity Vault:** Google Sign-In (Firebase Auth) links to a `/squelch/` folder in the user's personal Google Drive. The identity keypair (Ed25519) and contacts list are stored in a zero-knowledge encrypted file (`vault.enc`).
* **Online Dual Transport (`rust-libp2p`):** Direct WebRTC/QUIC data streams with automatic STUN/TURN traversal and E2E encrypted Circuit Relays.
* **Offline Mesh Network:** Auto-detection of network loss fallback to **Bluetooth Low Energy (BLE)** and **Wi-Fi Direct** (Google Nearby Connections on Android, Multipeer Connectivity on iOS).

---

## 2. Authentication & Google Drive Vault (`/squelch/`)

### 2.1 Firebase Auth & Scope Configuration
* **Authentication Method:** Firebase Google Sign-In.
* **OAuth Scope:** `https://www.googleapis.com/auth/drive.file` (limits access exclusively to files and folders created by Squelch).
* **Drive Directory Path:** `/Google Drive/squelch/vault.enc`

### 2.2 Key Derivation & Zero-Knowledge Encryption Flow
1. Device generates a **24-word BIP-39 Mnemonic Seed**.
2. Key Derivation:
   * Private Identity Key (`sk_identity`)
   * Public Identity / DID (`did:squelch:<hex_pk>`)
3. User creates a 6-digit **Master PIN**.
4. Key derivation via **Argon2id**:
   $$	ext{K\_vault} = 	ext{Argon2id}(	ext{Master\_PIN}, 	ext{Salt} = 	ext{SHA256}(	ext{Google\_UID}))$$
5. Payload encrypted with **AES-256-GCM** using $	ext{K\_vault}$ and stored at `/squelch/vault.enc`.

---

## 3. Step-by-Step Android Implementation Guide

### Step 3.1: Environment & Dependencies (`build.gradle.kts`)
```kotlin
dependencies {
    // Firebase Auth & Google Drive
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")

    // Offline Mesh: Google Nearby Connections API
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Encrypted Storage
    implementation("net.zetetic:sqlcipher-android:4.5.4@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
}
```

### Step 3.2: Android Manifest Permissions (`AndroidManifest.xml`)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Internet & Network State -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- Bluetooth & Nearby Connections Mesh -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:usesPermissionFlags="neverForLocation" />
</manifest>
```

### Step 3.3: Android Mesh Service Implementation (Google Nearby Connections)
```kotlin
class AndroidMeshManager(private val context: Context, private val localEndpointName: String) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.squelch.p2p.mesh"

    fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )
    }

    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        )
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept secure P2P connection handshake
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("SquelchMesh", "Connected to endpoint: $endpointId")
            }
        }
        override fun onDisconnected(endpointId: String) {}
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                // Pass encrypted payload to Rust core via JNI
                NativeBridge.receiveMeshPacket(bytes)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}
```

---

## 4. Step-by-Step iOS Implementation Guide

### Step 4.1: Info.plist Permissions & Network Keys
Add the following keys to `Info.plist`:

```xml
<!-- Bluetooth Usage Descriptions -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Squelch uses Bluetooth to route encrypted messages when internet is unavailable.</string>

<key>NSLocalNetworkUsageDescription</key>
<string>Squelch uses local Wi-Fi to discover nearby P2P chat peers.</string>

<!-- Multipeer Connectivity Bonjour Services -->
<key>NSBonjourServices</key>
<array>
    <string>_squelch-mesh._tcp</string>
    <string>_squelch-mesh._udp</string>
</array>
```

### Step 4.2: iOS Multipeer Connectivity Engine (`IOSMeshManager.swift`)
```swift
import Foundation
import MultipeerConnectivity

class IOSMeshManager: NSObject, MCSessionDelegate, MCNearbyServiceAdvertiserDelegate, MCNearbyServiceBrowserDelegate {
    private let serviceType = "squelch-mesh"
    private let myPeerID: MCPeerID
    private var session: MCSession!
    private var advertiser: MCNearbyServiceAdvertiser!
    private var browser: MCNearbyServiceBrowser!

    init(displayName: String) {
        self.myPeerID = MCPeerID(displayName: displayName)
        super.init()
        
        self.session = MCSession(peer: myPeerID, securityIdentity: nil, encryptionPreference: .required)
        self.session.delegate = self
        
        self.advertiser = MCNearbyServiceAdvertiser(peer: myPeerID, discoveryInfo: nil, serviceType: serviceType)
        self.advertiser.delegate = self
        
        self.browser = MCNearbyServiceBrowser(peer: myPeerID, serviceType: serviceType)
        self.browser.delegate = self
    }

    func startMesh() {
        advertiser.startAdvertisingPeer()
        browser.startBrowsingForPeers()
    }

    func stopMesh() {
        advertiser.stopAdvertisingPeer()
        browser.stopBrowsingForPeers()
    }

    // MARK: - MCNearbyServiceBrowserDelegate
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String : String]?) {
        // Invite discovered peer to session
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 10)
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {}

    // MARK: - MCNearbyServiceAdvertiserDelegate
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        invitationHandler(true, self.session)
    }

    // MARK: - MCSessionDelegate Payload Handling
    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        // Forward encrypted data payload to Rust core engine via C-FFI
        data.withUnsafeBytes { rawBuffer in
            if let baseAddress = rawBuffer.baseAddress {
                process_incoming_mesh_packet(baseAddress.assumingMemoryBound(to: UInt8.self), UInt(data.count))
            }
        }
    }

    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {}
    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}
    func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {}
    func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {}
}
```

---

## 5. Cross-Platform Framework Bridge (Flutter Integration)

When implementing in **Flutter**, use native platform channels to trigger platform-specific mesh networks while delegating crypto and P2P logic to a compiled Rust core library (`.so` for Android, `.xcframework` for iOS).

```
+------------------------------------------------------------------+
|                     Flutter Dart UI Layer                        |
+------------------------------------------------------------------+
          |                                       |
    (Platform Channels)                       (dart:ffi)
          |                                       |
          v                                       v
+------------------------+             +---------------------------+
| Native Mesh Adapters   |             | Compiled Rust Core        |
| - Android Nearby API   |             | - Signal Double Ratchet   |
| - iOS Multipeer framework|           | - rust-libp2p (Online)    |
+------------------------+             | - SQLCipher Database      |
          |                                       |
          +-------------------+-------------------+
                              |
                              v
             [Unified Message Protocol Dispatcher]
```

---

## 6. Comprehensive Phase-by-Phase Developer Implementation Plan

### Phase 1: Authentication & Drive Integration
1. Configure Firebase Project & register package IDs (`com.squelch.p2p` & `org.squelch.p2p`).
2. Integrate Google Sign-In with OAuth scope `https://www.googleapis.com/auth/drive.file`.
3. Implement `DriveVaultManager`:
   * Check for directory `/squelch/`.
   * Create folder if missing.
   * Read/Write `vault.enc`.

### Phase 2: Crypto Core & Local Database (Rust + SQLCipher)
1. Write Rust core library exposing C-FFI functions:
   * `generate_identity_keys()`
   * `encrypt_vault_payload()` / `decrypt_vault_payload()`
   * `encrypt_message_ratchet()` / `decrypt_message_ratchet()`
2. Set up local SQLCipher database with schemas (`identity`, `contacts`, `messages`, `mesh_queue`).

### Phase 3: Online Transport Layer (`rust-libp2p`)
1. Implement `rust-libp2p` swarm with WebRTC, QUIC, and Circuit Relay v2 protocols.
2. Build connection manager that monitors system network reachability.

### Phase 4: Native Offline Mesh Drivers
1. **Android:** Implement `AndroidMeshManager` using Google Nearby Connections API.
2. **iOS:** Implement `IOSMeshManager` using Apple `MultipeerConnectivity` framework.
3. Wire up native callbacks to pass payloads directly into Rust C-FFI message parser.

### Phase 5: UI & Device Migration Testing
1. Onboarding UI: Sign in with Google $ightarrow$ Enter Master PIN $ightarrow$ Fetch or Create Identity.
2. QR Code Exchange UI for adding contacts.
3. Test device migration: Log in on Phone B, enter Master PIN, restore contacts from Google Drive, and verify active identity re-binding.
4. Test offline mesh: Turn off Wi-Fi/Cellular on both phones, place them in physical proximity, and verify message delivery over BLE/Wi-Fi Direct.

---
*End of Squelch Mobile Developer Specification Document.*