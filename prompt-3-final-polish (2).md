# Prompt 3 — Final Polish: Remaining Chat Features, Non-Functionals, Ship Quality

Assume `prompt-1-mvp.md` (music) and `prompt-2-core-features.md` (E2EE 1:1 chat + clip sharing) are built.

## Remaining feature checklist

### Chat
- [ ] Media: camera capture, multi-select gallery, video with thumbnail + compression, voice notes (hold-to-record, waveform, playback speed)
- [ ] **Location share:** one-time pin and optional live location for a fixed duration; OSM/MapLibre preview; coordinates only inside the encrypted payload
- [ ] **Forward** message/media: multi-select, multiple chats, "Forwarded" label, re-encrypt per recipient, never plaintext via server
- [ ] Reply, delete for me / for everyone, edit (time-limited), copy, star
- [ ] **Groups:** create, add/remove members, admin roles, Sender Keys with rotation on membership change (biggest risk — test heavily)
- [ ] Typing indicator, last-seen with privacy toggle, read-receipts toggle
- [ ] In-chat search; media/docs/links gallery per conversation
- [ ] **Stickers:** built-in pack; send/receive WebP (512×512, ≤100 KB static); received sticker → "Add to my stickers"; import packs via **WhatsApp-compatible format** (`.wastickers` / `contents.json` + WebP) and the Android share intent Sticker.ly-style apps expose (Sticker.ly has no public API — import by share/export, not scraping); pack manager with reorder, delete, favorites, recents

### Music extras
- [ ] Dynamic colors from album art, equalizer (`android.media.audiofx`), playback speed
- [ ] Per-song actions: add to playlist/queue, share clip, share file, delete, info
- [ ] Sort/filter: title, artist, album, date added, folder
- [ ] Optional online lyrics provider (e.g. LRCLIB) behind an interface

## Non-functional concerns
- **Errors:** network loss, 5xx, failed decrypt, resumable transfers.
- **States:** skeleton loaders, friendly empty screens (no folder picked, no chats, no results, no lyrics).
- **Responsive:** phone + tablet/foldable, landscape, dark/light/dynamic color, edge-to-edge.
- **Accessibility:** content descriptions, 48 dp targets, TalkBack order, font scaling, contrast.
- **Security:** optional certificate pinning, no secrets in repo, R8 rules for libsignal, logs scrubbed of PII/keys, signed upload URLs.
- **Permissions:** minimal and runtime (`READ_MEDIA_AUDIO`, notifications, camera, mic, location only when used) with rationale screens.
- **Battery/perf:** WorkManager constraints, no wakelocks outside playback, Coil caching, stable lazy-list keys.
- **Config:** dev/staging/prod variants, base URLs and API keys via BuildConfig/secrets plugin.
- **Testing:** unit tests (crypto wrapper, repositories, sleep timer, shuffle, LRC parser); instrumented tests (send/receive, library scan); backend pytest (auth/keys/messages).
- **Legal:** in-app notice that downloads and clip sharing must use content the user has rights to; license badges in Discover; privacy policy stub (server stores only ciphertext).

## Code quality
Remove dead code and unused deps, consistent naming, KDoc on crypto and trimming logic, ktlint/detekt and ruff/black clean, README with setup, architecture diagram, how to add a `MusicSourceProvider`.

## Instruction
**Do a final pass. Fix all rough edges. The output should be production-ready.**
