package main

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin:     func(r *http.Request) bool { return true },
}

type Peer struct {
	conn     *websocket.Conn
	edPubHex string
	writeMu  sync.Mutex
}

type Hub struct {
	mu    sync.RWMutex
	peers map[string]*Peer
}

type Frame struct {
	T      string   `json:"t"`
	K      int      `json:"k,omitempty"`
	F      string   `json:"f,omitempty"`
	P      string   `json:"p,omitempty"`
	Peers  []string `json:"peers,omitempty"`
}

func (h *Hub) add(p *Peer) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.peers[p.edPubHex] = p
}

func (h *Hub) remove(edPubHex string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if p, ok := h.peers[edPubHex]; ok {
		_ = p.conn.Close()
		delete(h.peers, edPubHex)
	}
}

func (h *Hub) roster() []string {
	h.mu.RLock()
	defer h.mu.RUnlock()
	var list []string
	for ed := range h.peers {
		list = append(list, ed)
	}
	return list
}

func (h *Hub) broadcast(frame Frame, except string) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for ed, p := range h.peers {
		if ed == except {
			continue
		}
		p.writeMu.Lock()
		_ = p.conn.WriteJSON(frame)
		p.writeMu.Unlock()
	}
}

func (h *Hub) serve(w http.ResponseWriter, r *http.Request) {
	edpubHex := r.Header.Get("X-Squelch-EdPub")
	if edpubHex == "" {
		http.Error(w, "missing X-Squelch-EdPub header", http.StatusBadRequest)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("upgrade failed: %v", err)
		return
	}

	p := &Peer{conn: conn, edPubHex: edpubHex}
	h.add(p)
	defer h.remove(edpubHex)

	log.Printf("peer connected: %s", edpubHex[:8]+"...")
	h.broadcast(Frame{T: "hi", Peers: h.roster()}, "")

	conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	conn.SetPongHandler(func(string) error {
		conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	go func() {
		ticker := time.NewTicker(30 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			p.writeMu.Lock()
			if err := p.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				p.writeMu.Unlock()
				return
			}
			p.writeMu.Unlock()
		}
	}()

	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			log.Printf("peer disconnected: %s err=%v", edpubHex[:8]+"...", err)
			return
		}

		var f Frame
		if err := json.Unmarshal(raw, &f); err != nil {
			continue
		}

		if f.T != "m" || f.K == 0 {
			continue
		}

		if f.F != edpubHex {
			log.Printf("sender mismatch: header=%s frame=%s", edpubHex[:8], f.F[:8])
			continue
		}

		h.broadcast(Frame{T: "m", K: f.K, F: f.F, P: f.P}, f.F)
	}
}

func main() {
	hub := &Hub{peers: make(map[string]*Peer)}

	http.HandleFunc("/v2/mesh", func(w http.ResponseWriter, r *http.Request) {
		hub.serve(w, r)
	})

	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("ok"))
	})

	port := "8080"
	log.Printf("Squelch relay starting on :%s", port)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
