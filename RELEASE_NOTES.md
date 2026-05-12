# Enigma2 FireTV — Release Notes

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
