# AI Voice + Animation Integration Plan

This document outlines the remaining work to move from the current local-text AI hooks to the full animated, voiced experience with ElevenLabs and per-player avatars.

## 1) Voice Stack (ElevenLabs) – Foundations
- Add deps: Retrofit/OkHttp + Media3 ExoPlayer (or simple MediaPlayer) for playback.
- Secure key: `ELEVENLABS_API_KEY` in `local.properties` → `BuildConfig` (no git).
- Client: `ElevenLabsClient` (POST `/v1/text-to-speech/{voice_id}`) with retries/backoff and rate-limit handling.
- Service: `AIVoiceService` with queueing, play/stop, volume, and lifecycle awareness.
- Caching: file cache keyed by phrase hash (LRU ~50MB) to avoid repeated synth calls.
- Settings UI: toggle voice on/off, select voice profile, test phrase button, volume slider.

## 2) Phrase Catalog & Context
- Author a JSON (e.g., `ai_phrases_v1.json`) with categories: game_start, turn_prompt, turn_result_positive/neutral/negative, chance_card, rivalry, streaks, win/lose, filler.
- Add light templating: `{name}`, `{nickname}`, `{streak}`, `{rival}`, `{tile}`, `{delta}`.
- Provide fallbacks for missing context (e.g., no rival data → neutral line).

## 3) Data Needed for Contextual Lines
- Ensure `playerId` is stored on all game/roll records and passed through to runtime state.
- Track per-profile stats locally: wins, losses, streak (current/longest), last placement, games played.
- Rivalry/enmity table keyed by `playerIdA`/`playerIdB` with counters (meet count, wins vs each other).
- Expose lightweight read API for the AI layer (e.g., `AIContextProvider`) to fetch current player’s stats + rival highlights quickly.

## 4) Event Hooks (where to speak)
- Game start: welcome + roster call-out (already text-based; add voice).
- Turn start: address player by nickname; mention streak/rival if relevant.
- Turn result: comment on tile/chance card and score delta.
- Special cards/tiles: drought, bonus, dock, elimination.
- Game end: winner line + mention placements/streaks.
- Error/offline: silent fallback (text bubble only).

## 5) Offline / Failure Fallback
- If TTS fails or is disabled: show text bubble only (current `LocalAIPresenter`).
- Timeouts: cap TTS wait (e.g., 1s); if exceeded, play cached or fallback text.
- Queue policy: drop or coalesce old lines when too many triggers fire.

## 6) Animation Layer
- Assets: Cloudie main avatar (loop), per-player water-drop avatars tinted to player color; light emotes for happy/sad/surprised.
- Format: Lottie (preferred) or lightweight frame animations; preload at AI Home.
- Triggers:
  - AI Home: Cloudie idle loop + player drops bobbing; intro text/voice sync.
  - Turn start: bring active player drop forward; simple bounce/pulse.
  - Turn result: play emote based on outcome (+/-/neutral).
  - Game end: winner flourish; others fade to back.
- Performance: keep JSON sizes small; reuse preloaded animations; avoid blocking UI thread.

## 7) Navigation & Flow Polish
- Current flow: ProfileSelection → AIHome → MainActivity. Polish AIHome to show:
  - Cloudie animation + greeting (voice/text).
  - Player row with colored drops, nicknames, player codes.
  - CTA: Start / Skip AI intro (already present; keep skip).
- Preserve selected profiles/colors through intents; ensure back navigation returns to selection cleanly.

## 8) UX Controls
- In-game toggle to mute AI voice (keeps text bubbles).
- “Replay last line” button (optional) using cached audio.
- Volume obeys system media volume.

## 9) Testing Checklist
- Voice: calls succeed <1s; playback without stutter; mute toggle works; cache hit for repeated phrases.
- Animations: no jank on low-end device; assets load once; correct player color mapping.
- Context: correct names/nicknames; streak/rival data shows where available; safe fallbacks otherwise.
- Navigation: ProfileSelection → AIHome → MainActivity round-trip; skip path works; saved-game resume path still functions.

## 10) Implementation Order (recommended)
1) Data & stats: ensure `playerId` in records; add streak/rival tables; expose `AIContextProvider`.
2) Voice plumbing: deps, key, client, service, cache, settings toggle.
3) Wire triggers: game start/turn/result/end call voice service with context; fallback to text if disabled.
4) Animations: add Lottie assets; hook to AIHome, turn start/result, game end.
5) Polish & QA: race conditions, queueing, offline behavior, performance on device.

## 11) Deliverables
- Code: `ElevenLabsClient`, `AIVoiceService`, `AIContextProvider`, settings screen, Lottie assets, updated hooks in `MainActivity`/`AIHomeActivity`.
- Data: phrase catalog JSON, migration for stats/rivalry, tests/QA notes.
- Docs: short readme on configuring API key, enabling voice, and adding new phrases/animations.
