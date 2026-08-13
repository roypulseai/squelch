# Squelch P2P — GitHub repo settings

This file collects the copy + commands needed to set up the
`github.com/roypulseai/squelch` repository so it shows the right
description, topics, and homepage on the web.

## Repo description (one-liner)

```
Serverless, peer-to-peer chat on your phone. Google Drive keeps your
identity, nearby phones keep your conversations. Noise-encrypted.
```

(Short form, ≤350 chars — GitHub's description field limit.)

## Topics (comma-separated tags)

```
p2p, mesh-networks, mobile, android, kotlin, compose, ble,
nearby-connections, noise-protocol, google-drive, ed25519, argon2,
sqlcipher, decentralized, chat
```

## Homepage URL

Set to `https://github.com/roypulseai/squelch#user-guide`.

## Social preview (1 MB PNG, 1280×640)

The `marketing/icon.svg` 512×512 vector is the source. Render the
PNG with:

```bash
tools/render-marketing-icons.sh
```

Then upload the resulting `marketing/out/icon-512.png` as the
organization avatar in **Settings → General → Social preview**.

## GitHub CLI commands (one-liners)

Set description + topics + homepage in one go:

```bash
gh repo edit roypulseai/squelch \
  --description "Serverless, peer-to-peer chat on your phone. Google Drive keeps your identity, nearby phones keep your conversations. Noise-encrypted." \
  --homepage "https://github.com/roypulseai/squelch#user-guide" \
  --add-topic p2p --add-topic mesh-networks --add-topic mobile \
  --add-topic android --add-topic kotlin --add-topic compose \
  --add-topic ble --add-topic nearby-connections \
  --add-topic noise-protocol --add-topic google-drive \
  --add-topic ed25519 --add-topic argon2 --add-topic sqlcipher \
  --add-topic decentralized --add-topic chat
```

## GitHub Pages (optional)

If you want the README rendered as a website under
`https://roypulseai.github.io/squelch`:

1. Settings → Pages → Source: **Deploy from a branch**.
2. Branch: `main`, folder: `/ (root)`.
3. A `docs/` folder with an `index.md` symlinked to the README is also
   fine if you want a separate landing page.

## Topics rationale

| Topic | Why |
|---|---|
| `p2p` | Core: peer-to-peer. |
| `mesh-networks` | Offline BLE/Wi-Fi Direct fallback. |
| `mobile` / `android` / `kotlin` | Stack. |
| `compose` | UI is Jetpack Compose. |
| `ble` / `nearby-connections` | The offline transport. |
| `noise-protocol` | E2E encryption. |
| `google-drive` | Vault storage. |
| `ed25519` / `argon2` / `sqlcipher` | The crypto stack. |
| `decentralized` | Differentiation vs. Signal/Telegram/WhatsApp. |
| `chat` | Category. |