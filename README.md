# Enigma2 FireTV

The app is not real polished yet, but getting there.  App was created by AI with my prompts.

A native Android / FireTV app built with **Kotlin**, the **Leanback** TV UI library and **ExoPlayer (Media3)** that connects to an **Enigma2** satellite/cable receiver running the **OpenWebif** plugin.

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
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── java/com/enigma2/firetv/
│   ├── data/
│   │   ├── api/         OpenWebifService.kt  ApiClient.kt
│   │   ├── model/       Models.kt
│   │   ├── prefs/       ReceiverPreferences.kt
│   │   └── repository/  Enigma2Repository.kt
│   └── ui/
│       ├── main/        MainActivity.kt
│       ├── setup/       SetupFragment.kt
│       ├── channels/    ChannelsFragment.kt  BouquetAdapter  ChannelAdapter
│       ├── epg/         EpgFragment.kt  EpgGridView.kt  EpgTimeRulerView.kt
│       ├── player/      PlayerActivity.kt
│       ├── settings/    SettingsActivity.kt  SettingsFragment.kt
│       └── viewmodel/   ChannelViewModel.kt  EpgViewModel.kt
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

## Stream URL Format

```
http://<receiver-ip>:8001/<service-reference>
```
The Enigma2 service reference looks like:  
`1:0:1:300:7:85:00C00000:0:0:0:`

## FireTV Remote Mapping

| Button | Action |
|---|---|
| D-pad Up/Down | Navigate channels / EPG rows |
| D-pad Left/Right | Navigate EPG events in time |
| OK / Select | Open channel / confirm event |
| Back | Return to previous screen / hide OSD |
| Play/Pause | Toggle OSD visibility during playback |

## License

MIT
