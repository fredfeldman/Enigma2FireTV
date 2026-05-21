# Enigma2 FireTV — Release Notes

## 1.5.0

This release brings Picture-in-Picture (PiP) streaming and includes all changes
from 1.4.0.

### Picture-in-Picture (PiP)

Press the **Home** button on your Fire TV remote while watching live TV and the
video automatically shrinks into a floating window in the corner of the screen.
You can browse other apps while the stream keeps playing.

- Returns to full-screen when you re-open the app.
- Disable in **Settings → Picture-in-Picture** if you prefer the stream to stop
  when you press Home (e.g. on low-RAM devices).
- Requires Fire OS 5+ (Android 8+).

---

## 1.4.0

This release re-enables the EPG Assign flow that was gated since 1.2.0 and adds
an EPGImport trigger button to the source browser.

### EPG Assign — now enabled

The full EPG source assignment flow is active for all receivers that have the
`openwebif-epgassign` plugin installed. A graceful *"plugin not found"* message
appears on receivers without it.

**Two entry points:**

- **Channel list** — long-press any channel → *Assign EPG source…*
- **EPG grid** — long-press any event → *Assign EPG source…* (new in 1.4.0)

**Flow:** pick an EPGImport source → pick the matching channel id from that
source (pre-ranked by similarity to the channel name) → confirm assignment →
optionally trigger an EPGImport run immediately. Existing assignments show an
*Unassign* entry so you can remove them in the same dialog.

### EPGImport — Run Import button

The EPGImport source browser (**Settings → EPGImport sources**) now has a
**Run Import** button in its toolbar. Tapping it sends a trigger to the
receiver's EPGImport plugin (`/web/epgrefresh`) and toasts the result. The
file-list **Refresh** button continues to re-fetch the source file list as
before. The subtitle text is updated to reflect both capabilities.

---

## 1.3.0

This release adds a standalone M3U/IPTV player, multi-source M3U management
integrated into the main channel list, channel favourites, channel-number
D-pad jump, a live channel text filter, and a read-only EPGImport source
browser.

### Standalone IPTV player

A new **IPTV** button in the main toolbar opens a self-contained M3U player
that works without an Enigma2 receiver:

- **Setup screen** — enter an M3U URL and an optional XMLTV EPG URL (defaults
  to EPGSHARE01's combined feed). The M3U is fetched, parsed, and cached to
  disk on first load.
- **Two-panel channel browser** — left panel lists M3U groups (plus an *All
  Channels* roll-up); right panel shows channels in the selected group.
- **EPG overlay** — now/next event data is fetched from the configured XMLTV
  URL in the background and shown on each channel row. The EPG is cached for
  6 hours before re-fetching.
- **Playback** — tapping a channel launches the existing `PlayerActivity` with
  the stream URL; no Enigma2 zap is performed.
- A **Refresh** button re-fetches the M3U and EPG on demand; a **Setup** button
  reopens the URL entry screen.

### Multi-source M3U management in the main channel list

M3U sources added here appear as bouquets alongside Enigma2 bouquets so IPTV
and satellite/cable channels sit in a single unified list:

- **Add M3U** toolbar button opens a dialog to enter a source name and M3U URL.
  The source is saved to `IptvPreferences` and loaded immediately.
- Multiple M3U sources are supported; each is cached separately on disk
  (`iptv_channels_<sourceId>.json`).
- Backward-compatible: a single URL previously saved by the standalone IPTV
  player is automatically migrated to the multi-source list on first use.
- IPTV channels in the unified list play directly from their stream URL without
  tuning the Enigma2 receiver; the context menu shows only the *Play* action
  for IPTV entries.

### Channel favourites

- Long-press any Enigma2 channel → **Add to favourites** / **Remove from
  favourites**. The toggle is immediate and persisted in `ReceiverPreferences`.
- A **Favourites** bouquet appears at the top of the bouquet list whenever at
  least one channel is pinned; it disappears automatically when the last
  favourite is removed.
- The channel list refreshes in place when viewing the Favourites bouquet so
  changes are visible without leaving the screen.

### Channel-number D-pad jump

Press the number keys on the Fire TV remote while the channel list is focused
to jump directly to a channel by position:

- Digits accumulate in a small on-screen overlay (e.g. *"47"*).
- After a 1.5-second pause the list scrolls to that channel number and moves
  focus to it.
- If additional digits arrive before the timeout the buffer extends, allowing
  three-digit channel numbers.

### Channel text filter

A text-input field above the channel list lets users type to filter channels by
name (case-insensitive, substring match). An empty-state message is shown when
no channels match. The filter is applied live as each character is typed.

### EPGImport source browser

**Settings → EPGImport sources** opens a read-only viewer of the
`*.sources.xml` files installed in `/etc/epgimport/` on the receiver:

- The file list is fetched via OpenWebif's `/file` controller; a **Refresh**
  button re-fetches it on demand.
- Tapping a file opens a detail view that lists each category header and the
  individual EPG sources it contains.
- Requires the EPGImport plugin on the receiver; shows an empty state
  gracefully if the plugin is absent or the directory is not accessible.

---

## 1.2.0

This release completes the FireTV port of Enigma2Android 1.5.1 with Phases 5–7:
external player routing, HDHomeRun lineup import, multi-room Zap-on, device
profile backup/restore, and a gated EPG Assign flow.

### Phase 5 — External players + IPTV + HDHomeRun import

- **PlaybackRouter**: each channel can now be played in the built-in ExoPlayer,
  a chosen external player (VLC, MX Player, Kodi), or prompted per play. Set the
  preference in Settings → Player.
- IPTV service references (`4097:…`) have their stream URL extracted and routed
  directly; HLS streams (`.m3u8`) get the correct MIME type.
- **HDHomeRun import**: in Bouquet Editor, tap the new "HDHomeRun…" button to
  pull a lineup from any HDHomeRun device on the LAN. Optionally skip encrypted
  channels and choose a transcode profile (default: heavy).

### Phase 6 — Multi-room + backup/restore

- **Zap on…**: channel context menu gains a "Zap on…" entry when ≥2 device
  profiles are configured. Tapping sends the channel to the selected receiver
  via the OpenWebif API.
- **Send Message** dialog now shows a "Send to" device picker when ≥2 profiles
  exist, so you can push a message to any receiver without switching active
  profiles.
- **Wake-on-LAN picker**: if multiple device profiles have a MAC address
  configured, a device picker is shown before sending the WOL packet.
- **Profile backup/restore**: Settings → Backup → Export device profiles saves
  all receiver profiles to `Downloads/Enigma2FireTV/` as JSON. Import merges
  them back, never overwriting existing passwords.

### Phase 7 — EPG Assign (gated)

- `EpgAssignDialog` is fully implemented and wired into the channel context
  menu, but guarded by the `BuildConfig.ENABLE_EPG_ASSIGN` flag (currently
  `false`). Flip the flag to enable the full source-picker → channel-picker →
  assign → optional EPGImport-trigger flow for builds targeting receivers that
  have the EPGAssign plugin installed.

---

## 1.1.0

This release brings over the receiver-control feature set that previously
shipped only on the phone / tablet build (Enigma2Android 1.5.1), trimmed to
make sense on a Fire TV remote. It is split across two App-Store pushes —
**1.1.0** ships **Phases 0 – 4** below; the upcoming **1.2.0** will add the
HDHomeRun-style picker, app-side parental app-lock screens, multi-device EPG
assign, and a few smaller polish items.

### Phase 0 — Capability probing & repository plumbing

- One-shot `probeCapabilities()` per session detects whether the receiver
  exposes parental control, transcoding, the `/api/config` tree, and WOL.
- Twenty-plus new repository endpoints + XML parsers added under
  `data/model/settings/` and `data/api/SettingsXml.kt`. Endpoints that 404
  are swallowed gracefully so older boxes degrade instead of crashing.

### Phase 1 — Receiver Settings hub (13 sub-screens)

A new **Settings → Receiver controls → Receiver settings…** entry opens a
dedicated activity that hosts:

- **Power & sleep** — standby, deep standby, wake up, reboot, restart GUI,
  sleep-timer minutes + action.
- **Audio & volume** — level slider with ±5 D-pad shortcuts, mute toggle.
- **Recording locations** — list, set default, add a new path, remove.
- **Tuner / signal** — live SNR / BER / signal, auto-refresh every 2 s.
- **Parental control** — protected-services list, change receiver setup PIN,
  optional app-side PIN gate (SHA-256 hashed locally).
- **Wake-on-LAN** — enable, wake-from-standby, location magic packet.
- **Transcoding** — dynamic key/value editor wired to the receiver profile.
- **OpenWebif Web UI** — session timeout, kiosk, autorefresh, hide adult,
  picons, name column.
- **All settings (advanced)** — browse every `/api/config` section and edit
  values with the right widget (bool → switch, choice → spinner, password →
  masked field).
- **Storage & mounts** — `mount` output plus per-disk SMART dump.
- **System log** — receiver log viewer with text filter and Save-to-Downloads.
- **Plugin manager** — list installed plugins, install by name, remove with
  confirmation.
- **Network info** — interfaces, IP, gateway, MAC, with a copy-to-clipboard
  helper.

### Phase 2 — Virtual remote control & on-screen messaging

- **Settings → Virtual remote** — D-pad-friendly layout that maps Fire TV
  remote keys to `/api/remotecontrol` codes.
- **Settings → Send message to receiver** — push an OSD message to the box
  with type (info / warning / error) and timeout.

### Phase 3 — Recording management + EPG reminders

- Rename, move, and tag receiver recordings from the Recordings screen.
- Per-event reminders backed by `AlarmManager` (uses
  `setAndAllowWhileIdle` so we don't need `SCHEDULE_EXACT_ALARM`),
  shown via `NotificationCompat` with **Watch now** and **Snooze 5 min**
  actions. `POST_NOTIFICATIONS` permission is requested on Android 13+.
- Conflict warning when two timers overlap on the same tuner.

### Phase 4 — EPG refresh, offline cache, and export

- **Refresh EPG** button on the EPG screen triggers
  `/api/epgrefresh` (or single-service refresh when viewing one channel).
- A `SharedPreferences`-backed `EpgCacheStore` keeps the last successful
  multi-service EPG payload per `(deviceId, bouquetRef)`, so the EPG still
  renders when the receiver is offline. A "cached EPG (n min old)" amber
  banner appears in that case.
- **Export EPG** writes per-channel **XMLTV** (`.xml`, UTC timestamps) or
  **JSON** to `Downloads/Enigma2FireTV/`, using `MediaStore.Downloads` on
  API 29+ and the legacy public-Downloads directory on older.

### What's intentionally not in 1.1.0

To keep the Fire TV build remote-friendly, phone-only items are excluded:
`sw600dp` layouts, Quick Settings tiles, app shortcuts, Picture-in-Picture,
and any wording that refers to phones, tablets, or cellular networks.

The HDHomeRun "heavy" picker, EPG-Assign (multi-device EPG merging), and a
few smaller polish items are gated behind `BuildConfig.ENABLE_EPG_ASSIGN`
and will ship in **1.2.0** (Phases 5 – 7).

---

## 1.0.4

This release focuses on user-experience polish, network resilience, accessibility, performance, and adds two major new features: an **AutoTimer manager** and a **Receiver Box info** viewer.

### New features

- **AutoTimer manager** — list, add, edit, enable/disable, delete, and trigger an immediate scan of AutoTimer rules on the receiver. Open from the **Timers** screen toolbar (**AutoTimers** button). Requires the AutoTimer plugin on the Enigma2 box; if it isn't installed the list is simply empty.
- **Receiver → Box info** in Settings — shows brand, model, image / kernel / Enigma2 / WebIF versions, uptime, MAC address, plus tuners, hard disks, and network interfaces (calls `/api/about`).
- **Tune receiver to channel** preference (Settings → Playback) — controls whether the receiver is also retuned when you pick a channel in the app. Default on; turn off to keep the receiver on whatever it was playing.
- **Auto-resume last channel** preference (Settings → Playback) — toggle the cold-launch auto-tune behaviour.
- **Drag-to-reorder playlists** with up/down buttons and immediate persistence.
- **Multi-select recordings** for bulk delete.

### Reliability

- New `RetryInterceptor` with exponential backoff for transient network failures.
- `NetworkUtils` connectivity checks before fetches; offline state surfaced in the UI.
- `ApiErrors` translator turns Retrofit / IO exceptions into user-friendly messages everywhere they're shown.
- Tap-to-retry banners on the channel list, EPG, recordings, timers, and AutoTimers.

### Performance

- `ChannelAdapter` now caches preferences and uses `PAYLOAD_DECORATIONS` for partial rebinds.
- `RecordingAdapter` uses `PAYLOAD_SELECTION` for targeted multi-select updates.
- `TimerAdapter` migrated to `ListAdapter` + `DiffUtil`.
- EPG grid uses pre-sorted caches to avoid per-frame sorting.
- Background timer polling continues every 15 minutes via WorkManager.

### Accessibility

- Focus highlights widened to 3 dp with `state_pressed` / `state_activated` selectors.
- Composite TalkBack content descriptions on channel rows, recordings, timers, EPG cells.

### Settings polish

- Live summaries on connection / auth fields.
- **Test Connection** action.
- **About** category (version + build code).
- New **Receiver** category (Box info).

### Bug fixes & internal

- MAC address validation in device setup.
- Password field show/hide toggle.
- Sort order persistence in Recordings.
- `AndroidViewModel`s where needed for safe context use.
- Delete-recording and timer enable/disable wired through repository.
- Settings reinitialises the API client after every change.
- `ViewBinding` enabled across the app.

### Known limitations

- AutoTimer time-window editing supports `from` / `to`; per-rule service restriction is preserved on edit but cannot yet be edited from the app.
- The Receiver → Box info dialog is read-only; it does not refresh automatically.

---

## Earlier versions

- **1.0.3** — multi-device profiles, EPG search, screenshot viewer, Wake-on-LAN.
- **1.0.2** — recordings playback queue, playlists.
- **1.0.1** — timer scheduling and notifications.
- **1.0.0** — initial release: live TV via OpenWebif, EPG grid, recordings browser.
