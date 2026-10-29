# Prompt 2 — Core Features: Encrypted 1:1 Chat + Clip Sharing

Assume the Stage 1 music app (`prompt-1-mvp.md`) is built and working. Do not break it.

## Assumptions
- Backend is a thin **relay** (server never sees plaintext): FastAPI + PostgreSQL + Redis + S3-compatible storage (MinIO in dev), run via docker-compose.
- Auth: username/phone + password, JWT with silent refresh. OTP provider pluggable (stub).
- Use **libsignal** (official). Never hand-roll crypto.

## New modules
```
:core:crypto      libsignal wrapper (X3DH, Double Ratchet)
:feature:chat     conversations, messages, attachments
backend/          app/{main.py,auth/,keys/,messages/,media/,ws/}, alembic/, docker-compose.yml
```
Upgrade `:core:database` to **SQLCipher**, key wrapped by Android Keystore (migrate existing music data).

## Data models
**Local:** `Contact(id, name, identityKey)`, `Conversation(id, type, title, lastMsgAt)`, `Message(id, convId, senderId, type[text|image|doc|clip], body, attachmentId?, status[sending|sent|delivered|read], ts)`, `Attachment(id, localPath, remoteUrl, key, sha256, mime, size)`.
**Server:** `users`, `devices`, `identity_keys`, `signed_prekeys`, `one_time_prekeys`, `pending_messages(recipient_device, ciphertext, created_at)`, `blobs(id, owner, size, expires_at)`.

## Features to build / optimize

### 1. E2EE 1:1 messaging
- Register/login; upload prekey bundle; fetch peer bundle; establish session.
- Send/receive text with sent/delivered/read receipts, offline queueing, WebSocket realtime, FCM wake-up with **no content in push payload**.
- Prekey replenishment, signed-prekey rotation, safety-number / QR verification, warning on identity-key change.
- Local DB encrypted; optional biometric app-lock; `allowBackup=false`.
- Attachments: random AES-256-GCM key per file, client-side encrypt, upload ciphertext blob, send key + URL + SHA-256 inside the Signal message. Blobs expire after delivery or 30 days.
- Image and document sharing (SAF picker, thumbnail, open with system viewer).

### 2. Song-clip sharing (standout feature)
- Reuse Stage 1 trimmer. New entry points: long-press song → "Share a part", player screen button, chat attachment menu → "Share song clip".
- Max 30 s (single constant). Send as encrypted audio attachment.
- Message payload carries title, artist, start/end offsets, original duration → bubble shows mini waveform + play button + "from: Title – Artist".
- Receiver plays inline; optional "Find full song" runs a search through `MusicSourceProvider`.
- Edge cases: DRM/protected files (disable with message), very short songs, VBR MP3 seek drift, sender deletes song afterwards (clip is independent).

### 3. Reliability
- Out-of-order messages, message before session exists ("waiting for key" bubble + retry/session reset), reinstall/key loss, expired JWT, server 5xx, upload/download resume via WorkManager.
- Test on real devices from aggressive-battery OEMs (Xiaomi/Oppo/Samsung).
- Backend: rate-limit auth + key endpoints, size-limited uploads, input validation.

## Instruction
**Optimize only the listed features. Don't refactor unrelated code.** Include a README section with threat model (what the server can and cannot see) and docker-compose setup.
