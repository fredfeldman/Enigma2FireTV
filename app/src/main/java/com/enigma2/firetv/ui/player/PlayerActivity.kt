package com.enigma2.firetv.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.PlayerView
import android.app.AlertDialog
import android.widget.Toast
import com.bumptech.glide.Glide
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Full-screen ExoPlayer activity for live streaming from Enigma2 receiver.
 *
 * Extras:
 *  - [EXTRA_STREAM_URL]    : full HTTP URL to the Enigma2 TS stream
 *  - [EXTRA_CHANNEL_NAME]  : display name for the channel
 *  - [EXTRA_SERVICE_REF]   : Enigma2 service reference (used to fetch EPG)
 */
class PlayerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_SERVICE_REF = "service_ref"
        /** Optional: pre-filled description shown in the OSD (used for recordings). */
        const val EXTRA_DESCRIPTION = "description"
        /** Optional: duration in seconds shown in the OSD (used for recordings). */
        const val EXTRA_DURATION_SEC = "duration_sec"
        /** Optional playlist support: list of stream URLs to play sequentially. */
        const val EXTRA_PLAYLIST_URLS = "playlist_urls"
        const val EXTRA_PLAYLIST_TITLES = "playlist_titles"
        const val EXTRA_PLAYLIST_DURATIONS = "playlist_durations"
        /** Index within the playlist arrays where playback should start (default 0). */
        const val EXTRA_PLAYLIST_INDEX = "playlist_index"

        private const val OSD_HIDE_DELAY_MS = 5_000L
    }

    private lateinit var playerView: PlayerView
    private lateinit var osdOverlay: View
    private lateinit var tvChannelName: TextView
    private lateinit var tvEventTitle: TextView
    private lateinit var tvEventTime: TextView
    private lateinit var tvNextEvent: TextView
    private lateinit var eventProgress: ProgressBar
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var ivChannelLogo: ImageView

    // Seek controls (visible only for seekable/recorded content)
    private lateinit var seekRow: View
    private lateinit var seekBar: SeekBar
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvSeekHint: TextView
    private lateinit var tvPauseStatus: TextView
    private lateinit var tvLiveSeekHint: TextView
    private var isSeekable = false
    private var userPaused = false

    // Playlist state
    private var playlistUrls: List<String> = emptyList()
    private var playlistTitles: List<String> = emptyList()
    private var playlistDurations: List<Int> = emptyList()
    private var playlistIndex: Int = 0
    private lateinit var tvPlaylistInfo: TextView

    private var player: ExoPlayer? = null
    private lateinit var prefs: ReceiverPreferences
    private val repository = Enigma2Repository()
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var currentStreamUrl: String = ""

    // Player feature controls
    private lateinit var tvBitrateOverlay: TextView
    private lateinit var btnAudio: TextView
    private lateinit var btnSubtitles: TextView
    private lateinit var btnAspect: TextView
    private lateinit var btnSleep: TextView
    // Aspect ratio cycling: FIT(0) → FILL(3) → ZOOM(4)
    private val resizeModes = intArrayOf(0, 3, 4)
    private val resizeModeLabels = arrayOf("Fit", "Fill", "Zoom")
    private var resizeModeIndex = 0
    private var sleepTimerRunnable: Runnable? = null

    private val hideOsdRunnable = Runnable { hideOsd() }
    private val clearSeekHintRunnable = Runnable { tvSeekHint.text = "" }
    private val clearLiveSeekHintRunnable = Runnable { tvLiveSeekHint.visibility = View.GONE }
    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            if (osdOverlay.visibility == View.VISIBLE && isSeekable) {
                handler.postDelayed(this, 1_000L)
            }
        }
    }
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateEventProgress()
            handler.postDelayed(this, 30_000L)   // refresh every 30 s
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        prefs = ReceiverPreferences(this)

        playerView = findViewById(R.id.player_view)
        osdOverlay = findViewById(R.id.osd_overlay)
        tvChannelName = findViewById(R.id.tv_channel_name)
        tvEventTitle = findViewById(R.id.tv_event_title)
        tvEventTime = findViewById(R.id.tv_event_time)
        tvNextEvent = findViewById(R.id.tv_next_event)
        eventProgress = findViewById(R.id.event_progress)
        loadingIndicator = findViewById(R.id.loading_indicator)
        tvError = findViewById(R.id.tv_error)
        ivChannelLogo = findViewById(R.id.channel_logo)
        seekRow = findViewById(R.id.seek_row)
        seekBar = findViewById(R.id.seek_bar)
        tvPosition = findViewById(R.id.tv_position)
        tvDuration = findViewById(R.id.tv_duration)
        tvSeekHint = findViewById(R.id.tv_seek_hint)
        tvPauseStatus = findViewById(R.id.tv_pause_status)
        tvLiveSeekHint = findViewById(R.id.tv_live_seek_hint)
        tvPlaylistInfo = findViewById(R.id.tv_playlist_info)
        tvBitrateOverlay = findViewById(R.id.tv_bitrate_overlay)
        btnAudio = findViewById(R.id.btn_audio)
        btnSubtitles = findViewById(R.id.btn_subtitles)
        btnAspect = findViewById(R.id.btn_aspect)
        btnSleep = findViewById(R.id.btn_sleep)
        btnAudio.setOnClickListener { showAudioTrackDialog() }
        btnSubtitles.setOnClickListener { showSubtitleDialog() }
        btnAspect.setOnClickListener { cycleAspectRatio() }
        btnSleep.setOnClickListener { showSleepTimerDialog() }

        // Read playlist extras
        playlistUrls = intent.getStringArrayListExtra(EXTRA_PLAYLIST_URLS) ?: emptyList()
        playlistTitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_TITLES) ?: emptyList()
        playlistDurations = intent.getIntegerArrayListExtra(EXTRA_PLAYLIST_DURATIONS) ?: emptyList()
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0)
        updatePlaylistInfoView()

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: run {
            showError(getString(R.string.stream_error))
            return
        }
        currentStreamUrl = streamUrl
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val serviceRef = intent.getStringExtra(EXTRA_SERVICE_REF) ?: ""

        tvChannelName.text = channelName

        // Load picon using same three-format fallback chain as channel list
        if (serviceRef.isNotBlank()) {
            val piconUrl = prefs.piconFallbackUrl(serviceRef)
            val piconUrlShort = prefs.piconFallbackUrlShort(serviceRef)
            val piconUrlName = prefs.piconFallbackUrlByName(channelName)
            Glide.with(this)
                .load(piconUrl)
                .error(
                    Glide.with(this)
                        .load(piconUrlShort)
                        .error(
                            Glide.with(this)
                                .load(piconUrlName)
                                .error(R.drawable.ic_channel_placeholder)
                        )
                )
                .into(ivChannelLogo)
        }

        initPlayer(streamUrl)

        // For recordings, show pre-supplied description instead of fetching live EPG
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val durationSec = intent.getIntExtra(EXTRA_DURATION_SEC, 0)
        if (serviceRef.isBlank() && description != null) {
            tvEventTitle.text = description
            if (durationSec > 0) {
                val h = durationSec / 3600
                val m = (durationSec % 3600) / 60
                val s = durationSec % 60
                tvEventTime.text = if (h > 0) "Duration: %d:%02d:%02d".format(h, m, s)
                                   else "Duration: %d:%02d".format(m, s)
            }
            // Offer resume if there is a saved position
            val savedPos = prefs.getPlaybackPosition(streamUrl)
            if (savedPos > 30_000L) {
                offerResume(savedPos)
            }
        } else {
            loadEpgInfo(serviceRef)
        }
    }

    // -------------------------------------------------------------------------
    // Resume playback
    // -------------------------------------------------------------------------

    private fun offerResume(savedPos: Long) {
        handler.postDelayed({
            val h = savedPos / 3_600_000L
            val m = (savedPos % 3_600_000L) / 60_000L
            val s = (savedPos % 60_000L) / 1_000L
            val posString = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.resume_title))
                .setMessage(getString(R.string.resume_message, posString))
                .setPositiveButton(getString(R.string.resume_yes)) { _, _ ->
                    player?.seekTo(savedPos)
                    showOsd()
                }
                .setNegativeButton(getString(R.string.resume_no)) { _, _ ->
                    prefs.clearPlaybackPosition(currentStreamUrl)
                }
                .show()
        }, 600L)
    }

    // -------------------------------------------------------------------------
    // Player
    // -------------------------------------------------------------------------

    private fun initPlayer(streamUrl: String) {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (prefs.username.isNotBlank()) {
                    val creds = android.util.Base64.encodeToString(
                        "${prefs.username}:${prefs.password}".toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
                    addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Basic $creds")
                                .build()
                        )
                    }
                }
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .also { exo ->
                playerView.player = exo

                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> showLoading(true)
                            Player.STATE_READY -> {
                                showLoading(false)
                                showOsd()
                            }
                            Player.STATE_ENDED -> {
                                showLoading(false)
                                // Clear saved position since playback finished
                                if (currentStreamUrl.isNotBlank()) {
                                    prefs.clearPlaybackPosition(currentStreamUrl)
                                }
                                advancePlaylist()
                            }
                            Player.STATE_IDLE -> showLoading(false)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showLoading(false)
                        showError("${getString(R.string.stream_error)}\n${error.message}")
                    }
                })

                // For recording file streams (/file?file=...) the URL has no .ts
                // extension, so explicitly set the MPEG-TS MIME type. Live streams
                // via port 8001 are also MPEG-TS.
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T)
                    .build()
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true
            }
    }

    // -------------------------------------------------------------------------
    // Playlist
    // -------------------------------------------------------------------------

    private fun updatePlaylistInfoView() {
        if (playlistUrls.size > 1) {
            tvPlaylistInfo.text = "${playlistIndex + 1} / ${playlistUrls.size}"
            tvPlaylistInfo.visibility = View.VISIBLE
        } else {
            tvPlaylistInfo.visibility = View.GONE
        }
    }

    private fun advancePlaylist() {
        val nextIndex = playlistIndex + 1
        if (nextIndex >= playlistUrls.size) {
            finish()
            return
        }
        playlistIndex = nextIndex
        val nextUrl = playlistUrls[nextIndex]
        val nextTitle = playlistTitles.getOrNull(nextIndex) ?: ""
        val nextDuration = playlistDurations.getOrNull(nextIndex) ?: 0

        tvChannelName.text = nextTitle
        tvEventTitle.text = ""
        tvEventTime.text = ""
        if (nextDuration > 0) {
            val h = nextDuration / 3600
            val m = (nextDuration % 3600) / 60
            val s = nextDuration % 60
            tvEventTime.text = if (h > 0) "Duration: %d:%02d:%02d".format(h, m, s)
                               else "Duration: %d:%02d".format(m, s)
        }

        isSeekable = false
        userPaused = false
        tvPauseStatus.visibility = View.GONE
        seekRow.visibility = View.GONE

        updatePlaylistInfoView()

        player?.also { exo ->
            val mediaItem = MediaItem.Builder()
                .setUri(nextUrl)
                .setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T)
                .build()
            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.playWhenReady = true
        }
        showOsd()
    }

    // -------------------------------------------------------------------------
    // EPG info
    // -------------------------------------------------------------------------

    private fun loadEpgInfo(serviceRef: String) {
        if (serviceRef.isBlank()) return
        lifecycleScope.launch {
            val events = repository.getEpgForService(serviceRef)
            val nowMs = System.currentTimeMillis()
            val nowEvent = events.find { it.beginMs <= nowMs && it.endMs > nowMs }
            val nextEvent = events.find { it.beginMs > nowMs }

            nowEvent?.let {
                tvEventTitle.text = it.title
                tvEventTime.text = "${timeFmt.format(Date(it.beginMs))} – ${timeFmt.format(Date(it.endMs))}"
            }
            nextEvent?.let {
                tvNextEvent.text = "${timeFmt.format(Date(it.beginMs))}  ${it.title}"
            }

            handler.post(progressUpdateRunnable)
        }
    }

    private fun updateEventProgress() {
        // Recalculate progress in case the event changed
        // (a full reload would be better for long-running sessions)
    }

    // -------------------------------------------------------------------------
    // OSD visibility
    // -------------------------------------------------------------------------

    private fun showOsd() {
        osdOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsdRunnable)
        if (!userPaused) {
            handler.postDelayed(hideOsdRunnable, OSD_HIDE_DELAY_MS)
        }
        // Show seek bar for seekable (recorded) content
        updateBitrateOverlay()
        val exo = player ?: return
        val dur = exo.duration
        if (dur > 0 && !exo.isCurrentMediaItemLive) {
            isSeekable = true
            seekRow.visibility = View.VISIBLE
            handler.removeCallbacks(seekBarUpdateRunnable)
            handler.post(seekBarUpdateRunnable)
        }
    }

    private fun hideOsd() {
        osdOverlay.visibility = View.GONE
        tvBitrateOverlay.visibility = View.GONE
        handler.removeCallbacks(seekBarUpdateRunnable)
    }

    private fun togglePause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
            userPaused = true
            tvPauseStatus.visibility = View.VISIBLE
            osdOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(hideOsdRunnable)
        } else if (exo.playbackState == Player.STATE_READY) {
            exo.play()
            userPaused = false
            tvPauseStatus.visibility = View.GONE
            showOsd()
        }
    }

    private fun updateSeekBar() {
        val exo = player ?: return
        val duration = exo.duration.coerceAtLeast(1L)
        val position = exo.currentPosition.coerceAtLeast(0L)
        seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        seekBar.progress = position.coerceAtMost(duration).toInt()
        tvPosition.text = formatMs(position)
        tvDuration.text = formatMs(duration)
    }

    /**
     * Seek within a live stream's buffer by [deltaMs] ms.
     * Forward: only allowed if there is enough buffered data ahead.
     * Backward: only allowed if we won't go before the start of the buffer window.
     */
    private fun seekInBuffer(deltaMs: Long) {
        val exo = player ?: return
        if (!exo.isCurrentMediaItemLive) return
        val currentPos = exo.currentPosition
        val targetPos = currentPos + deltaMs
        if (deltaMs > 0) {
            // Jump forward only if the buffer extends far enough
            if (exo.bufferedPosition < targetPos) return
        } else {
            // Jump backward only if we stay within the available window
            if (targetPos < 0) return
        }
        exo.seekTo(targetPos)
        showOsd()
        val label = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        tvLiveSeekHint.text = label
        tvLiveSeekHint.visibility = View.VISIBLE
        handler.removeCallbacks(clearLiveSeekHintRunnable)
        handler.postDelayed(clearLiveSeekHintRunnable, 1_500L)
    }

    /** Seek forward (positive) or backward (negative) by [deltaMs] milliseconds. */
    private fun seek(deltaMs: Long) {
        val exo = player ?: return
        if (!isSeekable) return
        val newPos = (exo.currentPosition + deltaMs).coerceIn(0L, exo.duration)
        exo.seekTo(newPos)
        showOsd()
        updateSeekBar()
        val label = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        tvSeekHint.text = label
        handler.removeCallbacks(clearSeekHintRunnable)
        handler.postDelayed(clearSeekHintRunnable, 1_500L)
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    // -------------------------------------------------------------------------
    // Loading / error
    // -------------------------------------------------------------------------

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        loadingIndicator.visibility = View.GONE
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // Key events (FireTV remote)
    // -------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                togglePause()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (userPaused && player?.playbackState == Player.STATE_READY) {
                    togglePause()
                } else {
                    if (osdOverlay.visibility == View.VISIBLE) hideOsd()
                    else showOsd()
                }
                true
            }
            // Left / rewind  → -30 s (recording) or -15 s within live buffer
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> when {
                isSeekable -> { seek(-30_000L); true }
                player?.isCurrentMediaItemLive == true -> { seekInBuffer(-15_000L); true }
                else -> super.onKeyDown(keyCode, event)
            }
            // Right / fast-forward → +30 s (recording) or +15 s within live buffer
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> when {
                isSeekable -> { seek(30_000L); true }
                player?.isCurrentMediaItemLive == true -> { seekInBuffer(15_000L); true }
                else -> super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_BACK -> {
                if (osdOverlay.visibility == View.VISIBLE) {
                    hideOsd()
                    true
                } else {
                    finish()
                    true
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                showPlayerOptionsMenu()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // -------------------------------------------------------------------------
    // Player options menu (Menu button on remote)
    // -------------------------------------------------------------------------

    private fun showPlayerOptionsMenu() {
        val options = arrayOf(
            "Audio Track",
            "Subtitles",
            "Aspect Ratio  [${resizeModeLabels[resizeModeIndex]}]",
            "Sleep Timer"
        )
        AlertDialog.Builder(this)
            .setTitle("Player Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAudioTrackDialog()
                    1 -> showSubtitleDialog()
                    2 -> cycleAspectRatio()
                    3 -> showSleepTimerDialog()
                }
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // Audio track selection
    // -------------------------------------------------------------------------

    private fun showAudioTrackDialog() {
        val exo = player ?: return
        val audioGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_audio_tracks), Toast.LENGTH_SHORT).show()
            return
        }
        val flatPairs = audioGroups.flatMap { g -> (0 until g.length).map { i -> Pair(g, i) } }
        val names = flatPairs.map { (g, i) ->
            val fmt = g.getTrackFormat(i)
            buildString {
                fmt.language?.let { append(Locale(it).displayLanguage) } ?: append("Track ${i + 1}")
                fmt.label?.let { append(" ($it)") }
                if (fmt.channelCount > 0) append(" ${fmt.channelCount}ch")
                if (g.isTrackSelected(i)) append(" ✓")
            }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.audio_track_title))
            .setItems(names.toTypedArray()) { _, which ->
                val (group, idx) = flatPairs[which]
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, idx))
                    .build()
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // Subtitle selection
    // -------------------------------------------------------------------------

    private fun showSubtitleDialog() {
        val exo = player ?: return
        val textGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_subtitle_tracks), Toast.LENGTH_SHORT).show()
            return
        }
        val names = mutableListOf(getString(R.string.subtitle_off))
        val pairs = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>?>(null)
        textGroups.forEach { g ->
            (0 until g.length).forEach { i ->
                val fmt = g.getTrackFormat(i)
                names.add(buildString {
                    fmt.language?.let { append(Locale(it).displayLanguage) } ?: append("Track ${i + 1}")
                    fmt.label?.let { append(" ($it)") }
                    if (g.isTrackSelected(i)) append(" ✓")
                })
                pairs.add(Pair(g, i))
            }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.subtitle_track_title))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    val (g, i) = pairs[which]!!
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        .build()
                }
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // Aspect ratio toggle
    // -------------------------------------------------------------------------

    private fun cycleAspectRatio() {
        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[resizeModeIndex]
        btnAspect.text = resizeModeLabels[resizeModeIndex]
        Toast.makeText(this, resizeModeLabels[resizeModeIndex], Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------------
    // Sleep timer
    // -------------------------------------------------------------------------

    private fun showSleepTimerDialog() {
        val mins = intArrayOf(0, 15, 30, 60, 90)
        val opts = arrayOf(
            getString(R.string.sleep_timer_off),
            "15 min", "30 min", "60 min", "90 min"
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sleep_timer_title))
            .setItems(opts) { _, which ->
                sleepTimerRunnable?.let { handler.removeCallbacks(it) }
                if (mins[which] > 0) {
                    val r = Runnable { finish() }.also { sleepTimerRunnable = it }
                    handler.postDelayed(r, mins[which] * 60_000L)
                    btnSleep.text = "${mins[which]}m"
                    Toast.makeText(this, getString(R.string.sleep_timer_set, mins[which]), Toast.LENGTH_SHORT).show()
                } else {
                    sleepTimerRunnable = null
                    btnSleep.text = getString(R.string.btn_sleep)
                }
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // Bitrate / resolution overlay
    // -------------------------------------------------------------------------

    private fun updateBitrateOverlay() {
        val vf = player?.videoFormat
        val af = player?.audioFormat
        if (vf == null && af == null) {
            tvBitrateOverlay.visibility = View.GONE
            return
        }
        tvBitrateOverlay.text = buildString {
            vf?.let {
                if (it.width > 0 && it.height > 0) append("${it.width}\u00d7${it.height}")
                if (it.frameRate > 0) append("  %.0ffps".format(it.frameRate))
                val br = it.peakBitrate.takeIf { b -> b > 0 } ?: it.averageBitrate
                if (br > 0) append("  ${br / 1000}kbps")
                append("\n")
            }
            af?.let {
                val codec = it.codecs?.substringBefore('.')
                    ?: it.sampleMimeType?.substringAfterLast('/')
                    ?: "audio"
                append(codec.uppercase())
                if (it.channelCount > 0) append(" ${it.channelCount}ch")
                if (it.sampleRate > 0) append("  ${it.sampleRate / 1000}kHz")
            }
        }.trim()
        tvBitrateOverlay.visibility = View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        // Save position for seekable (recorded) content so we can offer resume later
        val exo = player ?: return
        if (isSeekable && currentStreamUrl.isNotBlank()) {
            val pos = exo.currentPosition
            val dur = exo.duration
            // Don't save if within 30 s of the end
            if (dur > 0 && (dur - pos) > 30_000L && pos > 5_000L) {
                prefs.savePlaybackPosition(currentStreamUrl, pos)
            } else if (dur > 0 && (dur - pos) <= 30_000L) {
                prefs.clearPlaybackPosition(currentStreamUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!userPaused) {
            player?.play()
        } else {
            tvPauseStatus.visibility = View.VISIBLE
            osdOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(hideOsdRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
