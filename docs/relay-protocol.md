# Squelch P2P online relay protocol

This document specifies the protocol between the Squelch Android/iOS
clients and the Squelch relay server. The relay is a small fan-out
service: it authenticates each client via OAuth, holds a peer roster,
and forwards ciphertext bytes between registered clients. End-to-end
encryption remains on the client; the relay never sees plaintext
chats.

## Transport

- WebSocket over TLS (`wss://`).
- URL path: `<relay-base>/mesh`.
- Default relay: `wss://relay.squelch.app/v2/mesh`.

## Auth

The client sends two headers on every (re)connect:

```
Authorization: Bearer <OAuth token>      # Google drive.file scope token
X-Squelch-EdPub: <hex-encoded ed25519 pubkey>
```

The relay MUST verify the bearer against the configured Google OAuth
client id (same one registered in `res/values/oauth_credentials.xml`),
extract the Google `sub` (UID), and bind the EdPub it sees in the
header to that UID. Subsequent frames whose `f` field maps to a
different EdPub under the same UID are rejected.

## Frames

All frames are JSON objects over a single text WebSocket message
(no binary frames). Fields:

| Field | Type | Meaning |
|-------|------|---------|
| `t` | string | message type: `"m"` (mesh), `"hi"`, `"bye"`, `"peers"` |
| `k` | int (0-255) | mesh frame kind (1 HELLO / 2 DATA / 3 HS) |
| `f` | string | sender's edPub hex |
| `p` | string | base64-encoded mesh payload |

### Client -> Relay

- `{ "t":"m", "k":1, "f":"<hex>", "p":"<b64>" }`   (HELLO/HS/DATA)

### Relay -> Client

- `{ "t":"hi", "peers":[<hex>, <hex>...] }`         (roster on connect)
- `{ "t":"m", "k":1, "f":"<hex>", "p":"<b64>" }`     (forwarded mesh frame)
- `{ "t":"bye", "f":"<hex>" }`                      (peer disconnected)

## Server reference

A minimal relay server implementation (Go):

```go
package main

import (
    "encoding/base64"
    "encoding/json"
    "net/http"
    "sync"
    "time"

    "github.com/gorilla/websocket"
)

type Peer struct {
    conn      *websocket.Conn
    edpubHex  string
    writeMu   sync.Mutex
}

type Hub struct {
    mu    sync.Mutex
    peers map[string]*Peer
}

func (h *Hub) add(p *Peer) { h.mu.Lock(); defer h.mu.Unlock(); h.peers[p.edpubHex] = p }
func (h *Hub) remove(edpubHex string) {
    h.mu.Lock(); defer h.mu.Unlock()
    if p, ok := h.peers[edpubHex]; ok { _ = p.conn.Close(); delete(h.peers, edpubHex) }
}
func (h *Hub) broadcast(frame any, except string) {
    h.mu.Lock(); defer h.mu.Unlock()
    for ed, p := range h.peers {
        if ed == except { continue }
        p.writeMu.Lock()
        _ = p.conn.WriteJSON(frame)
        p.writeMu.Unlock()
    }
}

type Frame struct {
    T string      `json:"t"`
    K int         `json:"k,omitempty"`
    F string      `json:"f,omitempty"`
    P string      `json:"p,omitempty"`
    Peers []string `json:"peers,omitempty"`
}

func (h *Hub) serve(w http.ResponseWriter, r *http.Request) {
    c, _ := websocket.Acceptor(w, r, nil)
    defer c.Close()
    edpubHex := r.Header.Get("X-Squelch-Edpub")
    if edpubHex == "" { return }
    p := &Peer{conn: c, edpubHex: edpubHex}
    h.add(p); defer h.remove(edpubHex)
    h.broadcast(Frame{T: "hi", Peers: roster(h)}, "")
    for {
        var f Frame
        if err := c.ReadJSON(&f); err != nil { return }
        if f.T != "m" || f.K == 0 { continue }
        h.broadcast(Frame{T:"m", K:f.K, F:f.F, P:f.P}, f.F)
    }
}

func main() {
    hub := &Hub{peers: map[string]*Peer{}}
    http.HandleFunc("/v2/mesh", hub.serve)
    http.ListenAndServe(":443", nil)
}
```

A real implementation MUST:
- Verify the OAuth bearer before accepting the WebSocket upgrade
  (do not accept anonymous peers).
- Rate-limit by Google UID (e.g. 60 frames/minute).
- Run over TLS with a valid cert (Let's Encrypt).
- Implement the WebSocket ping/pong handler with a 60 s heartbeat;
  drop peers that miss 3 pongs.

## Test vector

A reference relay runs at `wss://relay.squelch.app/v2/mesh` once
production credentials are wired in. Until then, the engine simply
fails to connect and stays offline; the rest of the mesh works.

## Future: libp2p direct upgrade

Once a Rust core is added to the project, this WebSocket relay will
become a thin compatibility shim. The libp2p transport with
Circuit Relay v2 + STUN + UDP hole-punch will be byte-compatible
with this protocol: clients will simply skip the relay when
direct connectivity is established.