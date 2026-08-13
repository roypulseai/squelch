import SwiftUI
import MultipeerConnectivity

/// Minimal SwiftUI scaffold that exercises `IOSMeshManager`.
/// This is the iOS analog of the Android `AppShell`. It displays the
/// status card, lets the user toggle the mesh, and lets them type a chat.
@main
struct SquelchAppApp: App {
    @StateObject private var viewModel = MeshViewModel(displayName: UIDevice.current.name)

    var body: some Scene {
        WindowGroup {
            MainView(viewModel: viewModel)
        }
    }
}

final class MeshViewModel: ObservableObject {
    @Published var meshStatus: MeshStatus = .idle
    @Published var peers: [MeshPeer] = []
    @Published var messages: [ChatMessage] = []
    @Published var input: String = ""
    @Published var selectedPeer: String? = nil

    private let manager: IOSMeshManager

    init(displayName: String) {
        manager = IOSMeshManager(displayName: displayName)
        manager.listener = self
    }

    func toggleMesh() {
        switch meshStatus {
        case .idle:
            manager.start()
            meshStatus = .running
        case .running:
            manager.stop()
            meshStatus = .idle
            peers.removeAll()
        }
    }

    func send() {
        guard !input.isEmpty, let peer = selectedPeer else { return }
        // The wire format is identical to MeshPacket.encode() on Android;
        // see the file-level comment in IOSMeshManager.
        let bytes = Array(input.utf8)
        let payload = Data(bytes)
        manager.broadcast(kindByte: 0x02, payload: payload)
        messages.append(ChatMessage(peerId: peer, body: input, mine: true))
        input = ""
    }
}

extension MeshViewModel: MeshListener {
    func onFrame(peerID: String, kind: UInt8, payload: Data) {
        if kind == 0x01 {
            // HELLO - record peer
            let peer = MeshPeer(peerId: peerID, displayName: peerID)
            DispatchQueue.main.async {
                if !self.peers.contains(where: { $0.peerId == peerID }) {
                    self.peers.append(peer)
                }
            }
        } else if kind == 0x02 {
            // DATA - decode MeshPacket in production. Scaffold shows it raw.
            let text = String(data: payload, encoding: .utf8) ?? ""
            DispatchQueue.main.async {
                self.messages.append(ChatMessage(peerId: peerID, body: text, mine: false))
            }
        }
    }
    func onEndpointConnected(peerID: String) {
        // HELLO arrives via onFrame afterward.
    }
    func onEndpointLost(peerID: String) {
        DispatchQueue.main.async {
            self.peers.removeAll { $0.peerId == peerID }
        }
    }
    func onError(message: String) {
        NSLog("Squelch mesh error: %@", message)
    }
}

struct MeshStatus { enum State { case idle, running } var s: State = .idle
    static var running: MeshStatus { MeshStatus(s: .running) }
    static var idle: MeshStatus { MeshStatus(s: .idle) }
}

struct MeshPeer: Identifiable {
    let peerId: String
    let displayName: String
    var id: String { peerId }
}

struct ChatMessage: Identifiable {
    let id = UUID()
    let peerId: String
    let body: String
    let mine: Bool
}

struct MainView: View {
    @ObservedObject var viewModel: MeshViewModel

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("SQUELCH P2P").font(.system(.largeTitle, design: .monospaced))
                Text(viewModel.meshStatus.s == .running ? "mesh: RUNNING" : "mesh: off")
                    .font(.system(.body, design: .monospaced))

                Button(viewModel.meshStatus.s == .running ? "STOP MESH" : "START MESH") {
                    viewModel.toggleMesh()
                }
                .font(.system(.body, design: .monospaced))

                if viewModel.peers.isEmpty {
                    Text("no peers yet")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(.secondary)
                } else {
                    ForEach(viewModel.peers) { p in
                        Button("\(viewModel.selectedPeer == p.peerId ? "* " : "")\(p.displayName)") {
                            viewModel.selectedPeer = p.peerId
                        }
                        .font(.system(.body, design: .monospaced))
                    }
                }

                Divider()
                ForEach(viewModel.messages) { m in
                    Text("\(m.mine ? "you" : m.peerId)  \(m.body)")
                        .font(.system(.body, design: .monospaced))
                }

                Spacer()
                HStack {
                    TextField("msg", text: $viewModel.input)
                        .textFieldStyle(.roundedBorder)
                    Button("SEND") { viewModel.send() }
                }
            }
            .padding()
        }
    }
}
