# Nido — Privacy-First Music Player & End-to-End Encrypted Messenger

Nido is an offline-first Android music player featuring Liquid Glass (Backdrop) Material 3 design, audio DSP (Equalizer, bass boost, pitch/speed controls), synchronized LRCLIB lyrics, and a zero-plaintext End-to-End Encrypted (E2EE) messenger powered by Signal Protocol and Sender Keys for group conversations.

---

## Architecture Overview

```mermaid
graph TD
    subgraph Android Application
        UI[Jetpack Compose + Backdrop UI]
        subgraph Features
            Chat[feature:chat<br/>1:1 & Group E2EE Chat]
            Clip[feature:clipshare<br/>Audio Trimmer & Clip Packaging]
            Music[feature:music<br/>ExoPlayer + DSP + Library]
            Discover[feature:discover<br/>Online Streaming & Downloads]
            Settings[feature:settings<br/>Themes, Privacy & Compliance]
        end
        subgraph Core Modules
            Crypto[core:crypto<br/>Signal Ratchet + Sender Keys]
            Database[core:database<br/>Room + SQLCipher Keystore]
            Network[core:network<br/>Retrofit + OkHttp WebSocket]
            Common[core:common<br/>Dispatchers, Models & Theme]
        end
    end

    subgraph Backend Relay (FastAPI)
        Auth[Auth & JWT Service]
        Keys[Signal PreKey Bundles]
        MsgRelay[Pending Message Dispatcher]
        Groups[Group Membership & Fanout]
        Blobs[Encrypted Blob Storage]
        WS[Real-time WebSocket]
    end

    Chat --> Crypto
    Chat --> Database
    Chat --> Network
    Music --> Database
    Music --> Clip
    Discover --> Network
    Network --> Auth
    Network --> Keys
    Network --> MsgRelay
    Network --> Groups
    Network --> Blobs
    Network --> WS
```

---

## Key Modules

- `:app` — Application entry point, Hilt dependency injection roots, navigation host, ProGuard/R8 configurations.
- `:core:crypto` — Signal Protocol cryptographic identity generation, Double Ratchet pairwise sessions, Sender Keys group encryption, and media AES-GCM streaming cryptor.
- `:core:database` — Room database encrypted at rest using SQLCipher with Keystore-backed key derivation.
- `:core:network` — WebSocket client for real-time delivery, typing status, presence, and Retrofit HTTP REST client.
- `:core:common` — Coroutine dispatchers, theme definitions, and base models.
- `:feature:chat` — E2EE 1:1 and Group conversations, media attachments (camera, video, voice notes, stickers, locations), message options (reply, forward, star, edit, delete).
- `:feature:music` — Android Media3 ExoPlayer background playback service, DSP equalizer, synchronized LRCLIB lyrics parser, library scan worker.
- `:feature:clipshare` — Interactive waveform audio trimmer generating shareable encrypted music clips.
- `:backend` — FastAPI asynchronous Python relay server storing zero plaintext.

---

## How to Add a New `MusicSourceProvider`

To integrate a new music source (e.g. Free Music Archive, Subsonic, or WebDAV) into Nido:

1. **Implement the Provider Interface** in `:feature:discover`:
```kotlin
interface MusicSourceProvider {
    val providerId: String
    val providerName: String
    
    suspend fun searchTracks(query: String, page: Int = 1): List<DiscoverTrack>
    suspend fun getStreamUri(trackId: String): Uri
    suspend fun downloadTrack(trackId: String, targetFile: File): Flow<DownloadProgress>
}
```

2. **Register via Hilt Multibindings** or provide as a Singleton:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CustomProviderModule {
    @Provides
    @IntoSet
    fun provideCustomSourceProvider(okHttpClient: OkHttpClient): MusicSourceProvider {
        return CustomMusicSourceProvider(okHttpClient)
    }
}
```

3. **Handle License and Attribution**:
   Ensure all fetched songs populate `DiscoverTrack.license` so license badges and copyright compliance dialogs render appropriately.

---

## Running the Backend

Requirements: Python 3.10+
```bash
cd backend
python -m venv .venv
source .venv/bin/activate  # Or .venv\Scripts\activate on Windows
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Run test suite:
```bash
pytest tests
```

---

## Building the Android App

Requirements: JDK 17, Android SDK 36 (minSdk 26).
```bash
# Run unit tests across all modules
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## Security & Compliance Notice

- **E2EE Guarantee**: The backend relay server stores only ciphertext. Message content, voice notes, locations, and stickers are never exposed in plaintext.
- **R8 / Proguard**: Native LibSignal and SQLCipher symbols are preserved in `app/proguard-rules.pro`.
- **Content Rights**: Users must ensure downloaded content and trimmed audio clips comply with applicable copyright and intellectual property rights.
