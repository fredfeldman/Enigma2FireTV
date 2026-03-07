# Developer Guide – Enigma2 FireTV

This guide covers everything needed to build, run, debug, and sideload the **Enigma2 FireTV** app on an Amazon Fire TV device.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Setup](#project-setup)
3. [Building the App](#building-the-app)
4. [Enabling ADB on Fire TV](#enabling-adb-on-fire-tv)
5. [Installing on Fire TV](#installing-on-fire-tv)
6. [Running via Android Studio](#running-via-android-studio)
7. [Receiver Requirements](#receiver-requirements)
8. [Configuration](#configuration)
9. [Troubleshooting](#troubleshooting)
10. [Project Structure](#project-structure)

---

## Prerequisites

### Development Machine

| Tool | Minimum Version | Download |
|---|---|---|
| Android Studio | Iguana (2023.2.1) or newer | https://developer.android.com/studio |
| JDK | 17 | Bundled with Android Studio |
| Android SDK | API 34 (Android 14) | Via Android Studio SDK Manager |
| Android Build Tools | 34.0.0 | Via Android Studio SDK Manager |
| Gradle | 8.2 (wrapper included) | Auto-downloaded |

> **Windows:** Ensure `JAVA_HOME` points to JDK 17 if running Gradle from the command line.

### Fire TV Device

- Amazon Fire TV Stick (2nd gen or later), Fire TV Stick 4K, or Fire TV Cube
- Connected to the **same local network** as your Enigma2 receiver
- Minimum Fire OS 6 (Android 7.1 base)

### Enigma2 Receiver

- Any Enigma2-based receiver (Vu+, Dreambox, Gigablue, etc.)
- **OpenWebif** plugin installed and running
- Stream port **8001** accessible on the local network

---

## Project Setup

### 1. Open the Project

```powershell
# Navigate to the workspace
cd D:\source\repos\Enigma2FireTV

# Open in Android Studio (optional — can just use the File > Open menu)
studio .
```

Or in Android Studio: **File → Open** → select the `Enigma2FireTV` folder.

### 2. Sync Gradle

Android Studio will prompt to sync when the project first opens. Click **Sync Now**.

From the command line:

```powershell
.\gradlew.bat --version   # verify Gradle wrapper works
.\gradlew.bat dependencies # optional: print dependency tree
```

### 3. Install Required SDK Components

Open **Tools → SDK Manager** in Android Studio and ensure these are installed:

| Component | Version |
|---|---|
| Android SDK Platform | API 34 |
| Android SDK Build-Tools | 34.0.0 |
| Android Emulator | Latest (optional) |
| Google USB Driver | Latest (for USB ADB) |

---

## Building the App

### Debug Build (recommended for development)

```powershell
# From the project root
.\gradlew.bat assembleDebug
```

Output APK:
```
app\build\outputs\apk\debug\app-debug.apk
```

### Release Build

```powershell
.\gradlew.bat assembleRelease
```

> **Note:** Release builds require a signing keystore. See [Android's signing docs](https://developer.android.com/studio/publish/app-signing) to create one, then add the following to `app/build.gradle.kts`:
>
> ```kotlin
> signingConfigs {
>     create("release") {
>         storeFile = file("your-keystore.jks")
>         storePassword = "yourStorePassword"
>         keyAlias = "yourKeyAlias"
>         keyPassword = "yourKeyPassword"
>     }
> }
> ```

### Build from Android Studio

1. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Click **Locate** in the notification to find the output APK.

---

## Enabling ADB on Fire TV

ADB debugging must be enabled on the Fire TV device before sideloading.

### Step-by-step

1. On the Fire TV home screen, go to **Settings → My Fire TV → About**
2. Click **Fire TV Stick** (or the device name) **7 times** to enable Developer Options
3. Go back to **Settings → My Fire TV → Developer Options**
4. Enable **ADB debugging** → **ON**
5. Enable **Apps from Unknown Sources** → **ON** (required for sideloading)
6. Note the Fire TV's IP address: **Settings → My Fire TV → About → Network**

---

## Installing on Fire TV

### Option A – Wi-Fi ADB (Recommended)

```powershell
# 1. Connect ADB to the Fire TV (replace with your Fire TV's IP)
adb connect 192.168.1.100:5555

# 2. Verify device is listed
adb devices

# 3. Install the debug APK
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected output:
```
Performing Streamed Install
Success
```

The app will appear in **Your Apps & Channels → Recent** on the Fire TV home screen.

### Option B – USB ADB

1. Connect the Fire TV to your PC via USB (requires USB OTG adapter for Stick devices)
2. Confirm ADB recognises the device: `adb devices`
3. Install the same as above — no IP needed when connected via USB.

### Uninstalling

```powershell
adb uninstall com.enigma2.firetv
```

### Updating (Re-install)

The `-r` flag in `adb install -r` retains app data across installs. Omit it to do a clean install.

---

## Running via Android Studio

### On a Physical Fire TV

1. Connect ADB as shown above.
2. In Android Studio, select the Fire TV device from the device drop-down (top toolbar).
3. Press **Run ▶** or **Shift+F10**.

Android Studio will build, install, and launch the app automatically, attaching the debugger.

### On an Android TV Emulator (for UI testing without a Fire TV)

1. Open **Tools → Device Manager → Create Device**
2. Select **TV** category → choose **1080p** or **720p**
3. System image: **API 34 (x86_64)**
4. Run the emulator and deploy normally.

> **Note:** The emulator cannot reach your physical Enigma2 receiver unless you configure network bridging. Use a physical Fire TV for full end-to-end testing.

---

## Receiver Requirements

| Requirement | Details |
|---|---|
| OpenWebif | Version ≥ 1.3.0 recommended. Install via Enigma2 plugin manager. |
| HTTP port | Default **80**. Configurable in the app settings. |
| Stream port | **8001** (Enigma2 built-in HTTP TS stream). Must not be firewalled. |
| Cleartext HTTP | The app has `android:usesCleartextTraffic="true"` enabled for local network use. |
| HTTPS | Supported — enable the **Use HTTPS** toggle in app Settings. |

### Verify OpenWebif is Working

Open a browser on the same network and navigate to:

```
http://<receiver-ip>/api/getallservices
```

You should receive a JSON response listing bouquets. If you see a login prompt, note your username/password for the app's Settings screen.

---

## Configuration

On first launch the app shows a **Setup screen**. Enter:

| Field | Example | Notes |
|---|---|---|
| Receiver IP | `192.168.1.50` | Hostname also works, e.g. `vuplusuno.local` |
| HTTP Port | `80` | Change if OpenWebif runs on a non-standard port |
| Username | `root` | Leave blank if OpenWebif has no authentication |
| Password | `••••` | Leave blank if no authentication |
| Use HTTPS | off | Enable only if OpenWebif is configured with TLS |

Settings can be changed at any time via the **Settings** button in the top-right of the channel list.

---

## Troubleshooting

### "adb: command not found"

Add the Android SDK `platform-tools` directory to your system PATH:

```powershell
# Add to your PowerShell profile or system environment variables:
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
```

### ADB connection refused / device not found

- Make sure **ADB debugging** is ON in Fire TV Developer Options.
- Confirm both devices are on the same Wi-Fi network.
- Try rebooting the Fire TV.
- Firewall on your PC may block port 5555 — add an inbound exception.

```powershell
# If the device shows "unauthorized":
adb disconnect 192.168.1.100:5555
adb connect 192.168.1.100:5555
# A dialog should appear on the Fire TV asking to allow the connection
```

### "INSTALL_FAILED_USER_RESTRICTED"

**Apps from Unknown Sources** is not enabled. Go to **Settings → My Fire TV → Developer Options → Apps from Unknown Sources → ON**.

### App shows "Cannot connect to receiver"

- Verify the IP address and port are correct.
- Open `http://<receiver-ip>/api/getallservices` in a browser to confirm OpenWebif is reachable from the same network.
- Check that no firewall on the receiver or router is blocking port 80 or 8001.

### Stream not playing / black screen

- Confirm Enigma2 stream port **8001** is reachable: `http://<receiver-ip>:8001/` in a browser (expect a 404 or empty response — that confirms the port is open).
- Try zapping the receiver to the target channel first from the OpenWebif web interface.
- Some receivers require HTTP Basic Auth for the stream — ensure username/password are set in the app.

### EPG shows no data

- Not all receivers populate `/api/epgmulti` instantly — wait a minute after the receiver boots.
- Confirm the bouquet service reference is correct by checking the OpenWebif web UI.

---

## Project Structure

```
Enigma2FireTV/
├── app/
│   ├── build.gradle.kts            App-level Gradle config, all dependencies
│   ├── proguard-rules.pro          ProGuard keep rules for Retrofit, Gson, ExoPlayer
│   └── src/main/
│       ├── AndroidManifest.xml     Activities, permissions, cleartext traffic flag
│       ├── java/com/enigma2/firetv/
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   ├── ApiClient.kt          OkHttp + Retrofit builder, stream URL helper
│       │   │   │   └── OpenWebifService.kt   Retrofit interface (bouquets, EPG, zap)
│       │   │   ├── model/
│       │   │   │   └── Models.kt             Data classes: Service, Bouquet, EpgEvent, etc.
│       │   │   ├── prefs/
│       │   │   │   └── ReceiverPreferences.kt SharedPreferences wrapper
│       │   │   └── repository/
│       │   │       └── Enigma2Repository.kt  Coroutine-based data access layer
│       │   └── ui/
│       │       ├── main/
│       │       │   └── MainActivity.kt       Single-activity host, setup vs. channels routing
│       │       ├── setup/
│       │       │   └── SetupFragment.kt      First-run IP/auth entry & connection test
│       │       ├── channels/
│       │       │   ├── ChannelsFragment.kt   Two-panel bouquet + channel list
│       │       │   ├── BouquetAdapter.kt     RecyclerView adapter for bouquet list
│       │       │   └── ChannelAdapter.kt     RecyclerView adapter with now/next + progress
│       │       ├── epg/
│       │       │   ├── EpgFragment.kt        Multi-channel EPG screen
│       │       │   ├── EpgGridView.kt        Custom canvas EPG grid (D-pad navigable)
│       │       │   └── EpgTimeRulerView.kt   Time ruler header
│       │       ├── player/
│       │       │   └── PlayerActivity.kt     ExoPlayer full-screen HLS player + OSD
│       │       ├── settings/
│       │       │   ├── SettingsActivity.kt   Settings host activity
│       │       │   └── SettingsFragment.kt   PreferenceFragmentCompat settings screen
│       │       └── viewmodel/
│       │           ├── ChannelViewModel.kt   Bouquet/channel LiveData + loading state
│       │           └── EpgViewModel.kt       Multi/single-service EPG LiveData
│       └── res/
│           ├── drawable/           Vector drawables, focus selectors, gradients
│           ├── layout/             XML layouts for all screens and list items
│           ├── mipmap-anydpi-v26/  Adaptive launcher icons
│           ├── values/             colors.xml, strings.xml, themes.xml, dimens.xml
│           └── xml/                preferences.xml (Settings screen)
├── build.gradle.kts                Root Gradle config (plugin versions)
├── settings.gradle.kts             Module inclusion + repository config
├── gradle.properties               JVM args, AndroidX flag
├── gradle/wrapper/
│   └── gradle-wrapper.properties   Gradle 8.2 distribution URL
└── README.md                       End-user overview and quick-start
```

---

## Key Dependencies

| Library | Version | Purpose |
|---|---|---|
| `androidx.leanback:leanback` | 1.2.0-alpha04 | TV / FireTV Leanback UI framework |
| `androidx.media3:media3-exoplayer` | 1.2.1 | HLS video playback engine |
| `androidx.media3:media3-exoplayer-hls` | 1.2.1 | HLS media source for Enigma2 streams |
| `com.squareup.retrofit2:retrofit` | 2.9.0 | OpenWebif REST API client |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client (auth interceptor, timeouts) |
| `com.google.code.gson:gson` | 2.10.1 | JSON deserialisation for API responses |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Async/non-blocking network calls |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.7.0 | MVVM ViewModel + LiveData |
| `com.github.bumptech.glide:glide` | 4.16.0 | Picon (channel logo) image loading |

---

*Last updated: March 2026*
