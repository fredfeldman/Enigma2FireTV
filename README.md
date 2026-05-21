# Enigma2 FireTV

A native Android / FireTV app built with **Kotlin**, the **Leanback** TV UI library and **ExoPlayer (Media3)** that connects to one or more **Enigma2** satellite/cable receivers running the **OpenWebif** plugin.

> App created with AI-assisted development.

## Features

| Feature | Details |
|---|---|
| Channel browsing | All bouquets & services fetched via OpenWebif REST API |
| EPG guide | Full multi-channel grid EPG with D-pad navigation |
| Live streaming | HLS/TS over HTTP via Enigma2's built-in stream server (port 8001) |
| Now/Next overlay | Shows current & next programme on every channel row |
| OSD | On-screen display with channel name, programme, time and progress bar |
| HTTP Basic Auth | Optional username/password for password-protected receivers |
| Settings | In-app settings screen to change receiver IP / port / credentials |
| **Multi-device** | Configure multiple Enigma2 receivers; switch with one button on the main screen |
| **Recordings** | Browse, search and play back recordings stored on the receiver |
| **Playlists** | Create and manage recording playlists; reorder entries, play sequentially with auto-advance |
| **Video folder** | Add video files from `/media/hdd/video` on the receiver directly into a playlist |
| **Virtual remote** (1.1.0) | Drive the receiver with the Fire TV remote — D-pad-friendly remote control screen |
| **Send message** (1.1.0) | Push an OSD info / warning / error message to the receiver with custom timeout |
| **Receiver settings hub** (1.1.0) | 13 sub-screens: power, audio, recording locations, tuner signal, parental control, WOL, transcoding, OpenWebif UI, all settings, storage / SMART, system log, plugin manager, network info |
| **EPG reminders** (1.1.0) | Per-event reminders with **Watch now** / **Snooze 5 min** notification actions; timer-conflict warnings |
| **Rename / move / tag recordings** (1.1.0) | Manage recordings on the receiver from the Recordings screen |
| **EPG refresh / cache / export** (1.1.0) | Trigger a server-side EPG refresh, fall back to a cached EPG when offline, export per-channel **XMLTV** or **JSON** to `Downloads/Enigma2FireTV/` |
| **External player routing** (1.2.0) | Choose to play channels in the built-in ExoPlayer, VLC, MX Player, or Kodi — or ask each time. Configured in Settings → Player |
| **HDHomeRun import** (1.2.0) | Import a complete channel lineup from any HDHomeRun tuner on the LAN into a new bouquet, with DRM-skip and transcode-profile options |
| **Zap on…** (1.2.0) | Instantly tune a secondary receiver to the selected channel from the channel context menu |
| **Multi-receiver Send Message** (1.2.0) | Pick which receiver receives the on-screen message when ≥2 profiles are configured |
| **WOL device picker** (1.2.0) | Wake-on-LAN prompts which receiver to wake when multiple devices have a MAC address |
| **Profile backup/restore** (1.2.0) | Export all receiver profiles to a JSON file in Downloads; import and merge on any device |
| **EPG Assign** (1.2.0, gated) | Full channel → EPG-source assignment flow via the EPGAssign plugin (disabled by default; enable with `ENABLE_EPG_ASSIGN=true` build flag) |

## Prerequisites

1. An Enigma2 receiver (e.g. Vu+, Dreambox, Gigablue) on your local network.
2. **OpenWebif** plugin installed and active on the receiver.
3. Stream port **8001** open (default Enigma2 stream port – no extra plugin needed).
4. A Fire TV Stick / Fire TV Cube on the **same network**.

## Build & Install

### Requirements
- Android Studio Iguana (2023.2.1) or newer
- JDK 17+
- Android SDK 34

### Steps

```bash
# Clone / open the workspace
cd Enigma2FireTV

# Build debug APK
./gradlew assembleDebug

# Sideload to Fire TV (enable ADB debugging in Fire TV Settings > My Fire TV > Developer Options)
adb connect <FireTV-IP>:5555
adb install -r app/build/outputs/apk/debug/Enigma2FireTV-debug-1.02.apk
```

## Project Structure

```
app/src/main/
├── java/com/enigma2/firetv/
│   ├── data/
│   │   ├── api/         OpenWebifService.kt  ApiClient.kt
│   │   ├── model/       Models.kt  DeviceProfile.kt  RecordingPlaylist.kt
│   │   ├── prefs/       ReceiverPreferences.kt  PlaylistPreferences.kt
│   │   └── repository/  Enigma2Repository.kt
│   └── ui/
│       ├── main/        MainActivity.kt
│       ├── setup/       SetupFragment.kt
│       ├── channels/    ChannelsFragment.kt  BouquetAdapter  ChannelAdapter
│       ├── devices/     DevicePickerFragment.kt  DeviceAdapter.kt
│       ├── epg/         EpgFragment.kt  EpgGridView.kt  EpgTimeRulerView.kt
│       ├── player/      PlayerActivity.kt
│       ├── playlists/   PlaylistManagerFragment.kt  PlaylistDetailFragment.kt
│       │                PlaylistAdapter.kt  PlaylistDetailAdapter.kt
│       │                VideoFileBrowserFragment.kt
│       ├── recordings/  RecordingsFragment.kt  RecordingAdapter.kt
│       ├── settings/    SettingsActivity.kt  SettingsFragment.kt
│       └── viewmodel/   ChannelViewModel.kt  EpgViewModel.kt  RecordingViewModel.kt
└── res/
    ├── layout/          All XML layouts
    ├── drawable/        Vector drawables & selectors
    ├── values/          strings / colors / themes / dimens
    └── xml/             preferences.xml
```

## OpenWebif API Endpoints Used

| Endpoint | Purpose |
|---|---|
| `GET /api/getallservices` | All bouquets |
| `GET /api/getservices?sRef=` | Channels in a bouquet |
| `GET /api/epgservice?sRef=` | EPG for a single service |
| `GET /api/epgmulti?bRef=` | Multi-service EPG for a bouquet |
| `GET /api/zap?sRef=` | Zap the receiver to a service |
| `GET /api/movielist` | All recordings |
| `GET /api/movielist?dirname=` | Recordings / videos in a specific folder |
| `GET /file?file=` | Stream a recording or video file by path |

## Stream URL Formats

**Live channel:**
```
http://<receiver-ip>:8001/<service-reference>
```
The Enigma2 service reference looks like: `1:0:1:300:7:85:00C00000:0:0:0:`

**Recording / video file:**
```
http://<receiver-ip>:<port>/file?file=<encoded-path>
```

## Multi-Device Support

Multiple receivers can be configured under **Switch Device** on the main screen. The app remembers the last-used device and reconnects to it automatically on launch. Tap **Switch Device** → **Add Device** to register a new receiver, or long-press an existing entry to edit or delete it.

## Playlists

1. Open **Playlists** from the main screen toolbar.
2. Create a playlist with **New Playlist**.
3. Inside a playlist tap **Add Video Files** to browse `/media/hdd/video` on the receiver, or go to **Recordings** and long-press any recording to add it.
4. Use ▲ / ▼ to reorder entries and ✕ to remove them.
5. Tap **▶ Play All** or tap any entry to start playback from that point. The player auto-advances through the queue.

## FireTV Remote Mapping

| Button | Action |
|---|---|
| D-pad Up/Down | Navigate channels / EPG rows / list items |
| D-pad Left/Right | Navigate EPG events in time |
| OK / Select | Open channel / confirm / play |
| Back | Return to previous screen / hide OSD |
| Play/Pause | Toggle pause / OSD visibility during playback |
| Fast-forward / Rewind | Seek forward / backward in recordings |

## License

MIT
