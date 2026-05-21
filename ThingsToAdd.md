# Enigma2Android v1.5.1 — Consolidated release

**Version:** 1.5.1 (versionCode 18)
**Previous public release:** 1.0.5

This release rolls up every change since the last public build (1.0.5).
It includes ten intervening internal versions (1.0.6 → 1.5.0) plus the
1.5.1 layout/UI polish. The headline additions are receiver
administration, multi-room support, HDHomeRun import, external-player
routing, EPG enhancements, recording management, and a phone-friendly
layout pass.

---

## Highlights since 1.0.5

- **Receiver Settings** — full read+write console for the box (1.0.6).
- **Virtual remote, on-TV messages, Quick-Settings tile, PiP, launcher
  shortcuts** (1.0.7).
- **Recording management + EPG reminders** (1.0.8).
- **Parental control editing, system log, plugin manager, network info,
  storage view** (1.1.0).
- **EPG refresh / export / offline cache** (1.1.1).
- **External player routing** (1.2.0).
- **Multi-room "zap on…" + profile export/import** (1.3.0).
- **Multi-room WOL + Send-message picker** (1.3.1).
- **EPG reminder action buttons (Watch now / Snooze 5 min)** (1.3.2).
- **HDHomeRun bouquet import + Android-side IPTV playback** (1.4.0).
- **EPG Assign (UI temporarily hidden in 1.5.1)** (1.5.0).
- **Phone-friendly responsive layouts** (1.5.1).

---

## 1.0.6 — Receiver Settings

A new top-level **Settings → Receiver settings** activity with nine
sub-screens covering everything OpenWebif exposes on the box:

1. **Power** — current state, toggle standby, enter standby, wake up,
   reboot, restart GUI, sleep-timer (minutes + action when up).
2. **Audio & Volume** — master volume slider, mute.
3. **Recording** — default location picker, list of known locations,
   add a new path, tap to remove.
4. **Tuner / Signal** — read-only live tuner status (type, tuner #, SNR,
   BER, signal).
5. **Parental control** — read-only viewer (active/inactive, type,
   setup-PIN status, list of protected services). Edit support arrives
   in 1.1.0.
6. **Wake-on-LAN setup** — receiver-side WOL: enabled, wake from
   standby, wake interface.
7. **Transcoding** — dynamic list from the transcoding plugin; each
   value editable.
8. **OpenWebif Web UI** — six toggles (picons, channel picons, channel
   details, responsive design, MovieDB, show all packages).
9. **All settings (advanced)** — generic browser of the entire
   `config.*` tree, rendered by item type (bool → switch, choice →
   list, password → masked, directory/text/int/float/slider → text).

The app probes capabilities once on entry and disables sub-screens the
receiver doesn't support. All saves run off the UI thread and toast
either *Saved.* or the receiver's error message. Empty 2xx responses are
treated as success so older OpenWebif builds work.

---

## 1.0.7 — Daily-use polish

- **Virtual remote control** — new *Remote* button in the channel-list
  toolbar opens an on-screen remote (D-pad, OK/Back/Exit/Menu,
  EPG/Info/Text, coloured keys, number pad, volume/channel/mute,
  play/pause/stop/record). Each press goes via `/api/remotecontrol`.
- **Send message to TV** — new *Message* button opens a dialog for a
  short message with info/warning/question/error level and a timeout,
  posted via `/api/message`.
- **Quick Settings tile** — drop the *Enigma2 Power* tile into the
  system Quick Settings panel to toggle the active receiver between
  standby and on; the tile reflects current state.
- **App shortcuts** — long-press the app icon for direct jumps to *Live
  TV*, *EPG*, *Recordings*.
- **Picture-in-Picture** — press Home during live TV and the player
  shrinks to a floating window. Toggle in
  *Settings → Playback → Picture-in-Picture* (on by default). Recording
  playback still uses the full player so resume positions are preserved.

---

## 1.0.8 — Recording management + EPG reminders

- **Rename a recording** — long-press → *Rename* (via `/api/movierename`).
- **Move to folder** — long-press → *Move to folder* → pick a known
  recording location (via `/api/moviemove`).
- **Mark watched / unwatched** — long-press toggles a `Watched` tag
  (via `/api/movietags`). A small dot appears next to watched titles.
- **Edit tags** — long-press → *Edit tags*; free-text editor that
  syncs the receiver-side tag list in a single round trip.
- **Schedule conflict warning** — when recording an EPG event the app
  fetches existing timers, computes overlap, and warns you before
  scheduling.
- **EPG reminders** — long-press a future event → *Remind me*. A local
  notification fires at programme start; no timer is created on the
  receiver, so no tuner is consumed. Uses inexact alarms so no
  SCHEDULE_EXACT_ALARM permission is required on Android 12+.

---

## 1.1.0 — Receiver admin

**Parental control (now editable):**
- Set / change setup PIN, protect a service by sref, unprotect a row.
- Optional app-side PIN gate (SHA-256 hashed, stored only on this
  device) for the in-app parental screen.

**New System category** in Receiver Settings:
- **Storage & mounts** — mountpoints with total/used/free where reported,
  plus a SMART information dump.
- **System log** — fetches recent receiver log, substring filter, Share
  to email/messaging/file.
- **Plugin manager** — list installed plugins, install by package name,
  remove with confirmation.
- **Network info** — interfaces with IP / netmask / gateway / MAC, with
  Copy to clipboard.

Mount/unmount, disk format, and a channel-scan UI were deferred — too
much variation across receiver images to ship safely.

---

## 1.1.1 — EPG quality

- **Refresh EPG** button next to the EPG search icon. Calls
  `/web/epgrefresh` when EPGImport is installed, else per-channel
  `/api/serviceupdateepg`. A toast confirms the request and the grid
  reloads.
- **Per-channel EPG export** — long-press a channel → *Export EPG*. Two
  files in `Downloads/`: `epg_<channel>_<ts>.xml` (XMLTV) and
  `epg_<channel>_<ts>.json` (raw event list). Written through
  `MediaStore` on Android 10+, so no storage permission is required.
- **Offline EPG cache** — every successful multi-EPG fetch is
  snapshotted (`epg_cache` SharedPreferences, one per bouquet). If a
  later fetch fails, the EPG screen falls back to the cached snapshot
  and shows a yellow banner *"Showing cached EPG (N min old)"*.

---

## 1.2.0 — External players

Route channel and recording playback to any installed video app — VLC,
MX Player, Kodi, etc. — instead of the built-in player. This is the
simplest path to "cast" Enigma2 streams: open the stream in Kodi or VLC
and use that app's AirPlay / Chromecast / DLNA support.

- **Settings → Playback → Player**: *Built-in player* (default) /
  *External player* / *Ask every time*.
- **Settings → Playback → Preferred external player**: pin a default
  (VLC / MX / Kodi auto-detected) so you skip the chooser, or "Show
  chooser each time".
- MIME type sent with the stream is `video/mp2t` for live and a sensible
  value for `.m3u8`, `.mp4`, `.mkv` recordings.
- Every existing playback path (channel tap, recording tap, schedule
  playback, favourites) honours the preference.

Chromecast was deliberately not added — its default media receiver
doesn't speak MPEG-TS, so shipping a Cast button would fail for nearly
every user. External players give a working path today.

---

## 1.3.0 — Multi-room and backup

- **Zap on another receiver** — long-press any channel → *Zap on…*. The
  menu lists every other configured device profile (only appears when
  you have more than one). Picking a receiver sends `/api/zap` to *that*
  box without disturbing the active one.
- **Profile export / import** — *Settings → Backup → Export device
  profiles* writes every profile to
  `Downloads/enigma2android_profiles_<timestamp>.json`. Passwords are
  stripped by default — tick *Include passwords (less secure)* for a
  fully self-contained backup. *Import* opens the system file picker;
  profiles with the same id are updated in-place, but a missing password
  never silently wipes your existing one.

---

## 1.3.1 — Multi-room polish

- **Wake-on-LAN picker** — the Wake button now pops a picker when more
  than one configured receiver has a MAC. Single-MAC setups keep the
  old one-tap behaviour.
- **Send-message picker** — the Send-message dialog gains a *Send to*
  spinner when you have more than one device profile. Messages to a
  non-active receiver go via the v1.3.0 `RemoteReceiverApi` so your
  active receiver / playback is untouched.

---

## 1.3.2 — Reminder notification actions

EPG reminder notifications now carry two action buttons:

- **Watch now** — launches the built-in player directly on the channel
  the reminder is for. Honours the active receiver's host/port/HTTPS.
- **Snooze 5 min** — dismisses the current notification and re-fires
  the reminder five minutes later. Uses the same inexact alarm path as
  v1.0.8, so no extra permission is needed.

The plain notification-body tap still auto-cancels as before.

---

## 1.4.0 — HDHomeRun bouquet import + Android-side IPTV playback

The Create-bouquet dialog (Settings → Edit bouquets → New bouquet) has
an opt-in checkbox **"Import channels from HDHomeRun tuner"**. When
ticked, the app contacts a SiliconDust HDHomeRun device on your network
and bulk-creates an Enigma2 bouquet containing every channel from its
lineup, each pointing at the HDHomeRun's HTTP MPEG-TS stream URL.

**How to import:** name the bouquet, tick the checkbox, enter the tuner
IP/hostname (default `hdhomerun.local`), leave *Skip encrypted (DRM)*
ticked, and pick a transcode profile:

- `heavy` — 1080p H.264 (recommended for tablets/phones)
- `mobile` — 720p H.264 (lower bitrate for cellular)
- `internet480` / `internet360` / `internet240`
- `none` — raw ATSC MPEG-2/AC-3; needs an external player like VLC

**How it works:** `GET /discover.json` to verify, `GET /lineup.json` to
read every channel, then the BouquetEditor plugin's `addbouquet` +
`addservicetobouquet` per channel, registering each as an Enigma2 IPTV
service ref `4097:0:1:0:0:0:0:0:0:0:<encoded-stream-url>:` with the
chosen `?transcode=<profile>` appended.

**Playback inside the app:** the app detects IPTV refs (types
4097/5001/5002/5003/8193) and plays the embedded URL directly instead
of asking the receiver's `:8001` streamserver to re-stream foreign
sources. Transcoded streams play natively in Media3; native ATSC streams
auto-route to an installed external player (VLC, MX, Kodi) across
Channels, EPG, EPG search, in-player channel-up/down, and Reminder
paths. The manifest now declares Android 11+ `<queries>` so external
players are visible to the chooser.

**Requirements:** BouquetEditor plugin on the receiver; HDHomeRun and
receiver on the same network; external player for native playback;
HDHomeRun hardware-transcoding model for transcoded profiles.

**Other fixes in 1.4.0:** the new-bouquet dialog now uses an explicit
dark theme so labels stay readable in light mode (fixes a
white-on-white regression); dialog switched to
`androidx.appcompat.app.AlertDialog` for proper Material theming.

---

## 1.5.0 — Assign EPG source (UI hidden in 1.5.1)

v1.5.0 added an in-app way to map an EPGImport source to a specific
channel via a new companion plugin `openwebif-epgassign` on the
receiver:

1. List EPGImport sources already configured on the box.
2. For the picked source, list every channel id it provides (parsed
   from that source's `*.channels.xml`), pre-ranked by similarity to
   the channel name.
3. Save a sref → channel-id override on the receiver and (optionally)
   trigger an EPGImport run immediately.

Existing mappings are detected so the picker offers an *Unassign* entry
for refs that already have an override. Intended audience: HDHomeRun /
IPTV / streaming refs that don't get EPG from the satellite/cable
transport — bind them to a free XMLTV feed in two taps.

**Status in 1.5.1:** UI entry points (the channel context menu item
*"Assign EPG source…"* and the EPG channel-name long-press) are
**temporarily hidden** while the `openwebif-epgassign` install path is
being stabilised across receiver images. The retrofit endpoints,
repository methods, and `EpgAssignDialog` are still in the build and
will be re-enabled in a future release — no settings need to be
migrated when the UI returns. Existing receiver-side assignments are
not affected.

---

## 1.5.1 — Phone-friendly responsive layouts

Two screens that were laid out for tablets now have dedicated phone
variants. Tablet (sw ≥ 600 dp) layouts are byte-equivalent to v1.5.0.

- **Recordings** — phones stack the list and the detail panel vertically
  (list on top, detail in a scrollable pane below). Tablets keep the
  side-by-side layout.
- **Channels** — phones move the bouquet list into a compact scrollable
  strip below the toolbar, freeing the whole screen width for the
  channel list (previously cramped to ~140 dp on a 360 dp phone).
  Tablets keep the bouquet side panel.
- **Padding / type / virtual remote** — container padding 24 dp → 16 dp,
  section titles 22 sp → 18 sp, virtual remote keys 64 × 56 dp / 3 dp
  margin → 48 × 48 dp / 2 dp margin on phones. Tablets keep the larger
  values.

All overrides live under `values-sw600dp/` and `layout-sw600dp/`. View
ids are identical across phone and tablet variants, so no fragment or
ViewBinding changes were needed.

---

## Compatibility

- Android: min SDK 26 (Android 8.0), target SDK 34. Quick Settings tile
  needs Android 7.0+, PiP needs Android 8.0+; the app silently skips
  both on older hardware.
- Receiver: stock OpenWebif works for most features. Specific extras
  require:
  - **BouquetEditor** plugin (bouquet editing, HDHomeRun import).
  - **EPGImport** plugin (Refresh EPG via plugin endpoint; EPG Assign
    once re-enabled).
  - **Transcoding** plugin (Receiver Settings → Transcoding sub-screen).
  - **AutoTimer** plugin (AutoTimers screen).
- All capability probes are tolerant: missing endpoints disable the
  matching sub-screen but never crash the app.

## Version

- versionCode 18 / versionName 1.5.1
- APKs:
  - `app/build/outputs/apk/debug/Enigma2Android-debug-1.5.1.apk`
  - `app/build/outputs/apk/release/Enigma2Android-release-1.5.1.apk`
