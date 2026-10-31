# Nido Backend — Zero-Plaintext Relay

A high-performance relay server for Nido:
- **Zero Plaintext Visibility**: Server relays Signal double-ratchet ciphertext and encrypted media blobs.
- **Client-First Music Download & Sync**: Audio downloads on device first $\to$ metadata syncs to account.
- **E2EE Attachments**: AES-256-GCM client encrypted blobs uploaded to S3/MinIO.

## Threat Model

| Data Item | Server Visibility | Threat Mitigation |
|---|---|---|
| Text Messages | Ciphertext only | Signal Double Ratchet + X3DH |
| Attachments & Clips | Encrypted blobs only | Client-side AES-256-GCM; key passed inside Signal envelope |
| Sender & Recipient ID | Visible (routing metadata) | Required for message delivery; purged upon delivery ACK |
| Prekeys & Signed Prekeys | Public keys only | Private keys never leave Android Keystore / client app |
| FCM Push Payload | Wake-up trigger only | Zero message body, sender name, or metadata in FCM payload |
| Local Database | Encrypted at rest | SQLCipher 256-bit AES key wrapped in Android Keystore |

---

## Quickstart (Docker Compose)

```bash
cd backend
docker-compose up -d --build
```

### Services
- **FastAPI Relay**: `http://localhost:8000` (docs at `http://localhost:8000/docs`)
- **PostgreSQL**: `localhost:5432` (`nido_db`)
- **Redis**: `localhost:6379`
- **MinIO Console**: `http://localhost:9001` (user: `minioadmin`, pass: `minioadminpassword`)
