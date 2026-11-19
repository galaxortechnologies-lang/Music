<div align="center">
  <h1>Nigutu</h1>
  <p><b>High-fidelity Android music streaming, real-time synced lyrics, offline playback, and audio DSP player.</b></p>
</div>

---

## Overview

Nigutu delivers a clean, ad-free music listening experience powered by a modular 22-module Kotlin & Jetpack Compose architecture, offline downloads, synchronized word-by-word lyrics, Music DNA track insights, environment-aware music recognition, USB DAC bit-perfect audio DSP, and Chromecast/Listen Together support.

- **Creator & Maintainer**: Anup Thakur ([Instagram](https://www.instagram.com/anup_thakurrrr/))
- **Repository**: [galaxortechnologies-lang/Music](https://github.com/galaxortechnologies-lang/Music)
- **License**: [GPL-3.0](LICENSE)

---

## Features

- **Streaming & Playback**: Ad-free streaming, audio/video switching, background playback, crossfade, offline download manager, Chromecast (GMS flavor), and Music Canvas support.
- **Audio DSP & Hardware**: Custom equalizer, spatial/DSP effects (`dsp-core`, `audio-dsp`), and external USB DAC bit-perfect audio routing (`usbaudio`).
- **Lyrics**: Multi-provider synchronized & word-by-word lyrics (`lyrics`, `betterlyrics`, `lrclib`, `kugou`, `simpmusic`, `paxsenixlyrics`, `youlyplus`) with romanization and translation.
- **Discovery & Recognition**: Acoustic track identification (`shazamkit`), Music DNA annotations, personalized queues, Charts, Podcasts, Moods, and Listen Together rooms.

---

## Building from Source

1. **Clone the Repository**

   ```bash
   git clone https://github.com/galaxortechnologies-lang/Music.git
   cd Music
   ```

2. **Optional API Secrets**

   Copy `.env.example` to `.env` (never commit `.env` or signing keystores) and configure any optional credentials you want to enable (`LASTFM_API_KEY`, `LASTFM_SECRET`, `GH_CLIENT_ID`, `GH_CLIENT_SECRET`, `FLOW_NEURO_API_KEY`).

3. **Run Unit Tests & Build Debug APK**

   ```bash
   ./gradlew --no-daemon --max-workers=2 testUniversalGmsDebugUnitTest
   ./gradlew --no-daemon --max-workers=2 assembleUniversalGmsDebug
   ```

   For the FOSS build without Google Play Services / Cast:

   ```bash
   ./gradlew --no-daemon --max-workers=2 assembleUniversalFossDebug
   ```

---

## Upstream Attribution & Special Thanks

Nigutu is derived from [Echo Music](https://github.com/EchoMusicApp/Echo-Music) and stands on the shoulders of the open-source Android music community:

| Project | Description |
| :-- | :-- |
| **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** | Upstream 22-module codebase and core architecture |
| **[Metrolist](https://github.com/MetrolistGroup/Metrolist)** & **[Vivi Music](https://github.com/vivizzz007/vivi-music)** | Foundational architecture & UI reference |
| **[ArchiveTune](https://github.com/koiverse/ArchiveTune)** | Material You UI inspiration |
| **[Better Lyrics](https://better-lyrics.boidu.dev/)** | Lyrics enhancement and synchronization |
| **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** | Lyrics implementation reference |
| **[Music Recognizer](https://github.com/aleksey-saenko/MusicRecognizer)** | Audio recognition engine |
| **[BravePipe](https://github.com/bravepipeproject/BravePipe)** | Stream decryption & backup playback engine |

Original copyright notices and contributor attributions are preserved under the [GNU General Public License v3.0](LICENSE).
