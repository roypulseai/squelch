import Foundation
import MultipeerConnectivity

/// Swift equivalent of Squelch's `AndroidMeshManager`. The Android side
/// uses Nearby Connections with P2P_CLUSTER; iOS uses MultipeerConnectivity
/// which maps onto the same Wi-Fi Direct + Bluetooth auto-discovery surface
/// in iOS 13+.
///
/// The listener gets bytes-after-kind-byte and bytes after the kind byte
/// of MeshPacket frames, identical to what AndroidMeshManager emits in Kotlin.
/// iOS-only Android-style constants:
///   0x01 = KIND_HELLO, 0x02 = KIND_DATA, 0x03 = KIND_HANDSHAKE.
///
/// Source-only: open in Xcode 14+ on macOS and add to a SwiftUI app
/// project to build. Cannot be compiled on Windows.
public final class IOSMeshManager: NSObject {

    public static let serviceType = "squelch-mesh"

    public weak var listener: MeshListener?

    private let myPeerID: MCPeerID
    private let session: MCSession
    private let advertiser: MCNearbyServiceAdvertiser
    private let browser: MCNearbyServiceBrowser

    private var outgoingPeers: [MCPeerID] = []

    public init(displayName: String) {
        self.myPeerID = MCPeerID(displayName: displayName)

        self.session = MCSession(
            peer: myPeerID,
            securityIdentity: nil,
            encryptionPreference: .required
        )

        self.advertiser = MCNearbyServiceAdvertiser(
            peer: myPeerID,
            discoveryInfo: nil,
            serviceType: IOSMeshManager.serviceType
        )

        self.browser = MCNearbyServiceBrowser(
            peer: myPeerID,
            serviceType: IOSMeshManager.serviceType
        )

        super.init()
        self.session.delegate = self
        self.advertiser.delegate = self
        self.browser.delegate = self
    }

    public func start() {
        advertiser.startAdvertisingPeer()
        browser.startBrowsingForPeers()
    }

    public func stop() {
        advertiser.stopAdvertisingPeer()
        browser.stopBrowsingForPeers()
        session.disconnect()
    }

    public func broadcast(kindByte: UInt8, payload: Data) {
        let header: [UInt8] = [kindByte]
        var body = Data(header)
        body.append(payload)
        guard !outgoingPeers.isEmpty else { return }
        do {
            try session.send(body, toPeers: outgoingPeers, with: .reliable)
        } catch {
            listener?.onError(message: "send failed: \(error)")
        }
    }

    private func frame(_ data: Data) -> (UInt8, Data)? {
        guard data.count >= 1 else { return nil }
        let kind = data[data.startIndex]
        let payload = data.advanced(by: data.startIndex + 1)
        return (kind, payload)
    }
}

extension IOSMeshManager: MCSessionDelegate {
    public func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        switch state {
        case .connected:
            outgoingPeers.append(peerID)
        case .notConnected, .connecting:
            outgoingPeers.removeAll { $0 == peerID }
            listener?.onEndpointLost(peerID: peerID.displayName)
        @unknown default:
            break
        }
    }

    public func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        if let (kind, payload) = frame(data) {
            listener?.onFrame(peerID: peerID.displayName, kind: kind, payload: payload)
        }
    }

    public func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}
    public func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {}
    public func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {}
}

extension IOSMeshManager: MCNearbyServiceAdvertiserDelegate {
    public func advertiser(_ advertiser: MCNearbyServiceAdvertiser,
                          didReceiveInvitationFromPeer peerID: MCPeerID,
                          withContext context: Data?,
                          invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        invitationHandler(true, session)
    }
}

extension IOSMeshManager: MCNearbyServiceBrowserDelegate {
    public func browser(_ browser: MCNearbyServiceBrowser,
                        foundPeer peerID: MCPeerID,
                        withDiscoveryInfo info: [String: String]?) {
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 10)
        listener?.onEndpointConnected(peerID: peerID.displayName)
    }

    public func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        listener?.onEndpointLost(peerID: peerID.displayName)
    }
}

public protocol MeshListener: AnyObject {
    func onFrame(peerID: String, kind: UInt8, payload: Data)
    func onEndpointConnected(peerID: String)
    func onEndpointLost(peerID: String)
    func onError(message: String)
}
