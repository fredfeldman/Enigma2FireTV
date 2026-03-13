package com.enigma2.firetv.ui.epg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.Timer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Custom canvas-based EPG grid view.
 *
 * Renders a time-based grid where:
 *   - Each row corresponds to a [Service]
 *   - Each column segment represents an [EpgEvent]
 *   - Horizontal axis = time  (1 minute = [PIXELS_PER_MINUTE] px)
 *   - The "now" marker is a vertical amber line
 *
 * Supports D-pad navigation for FireTV remote.
 */
class EpgGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ---- Layout constants ----
    private val rowHeight = context.resources.getDimensionPixelSize(R.dimen.epg_row_height)
    private val eventPadding = 4f
    private val cornerRadius = 6f

    companion object {
        const val PIXELS_PER_MINUTE = 6f          // 1 min → 6 px (so 1 hr → 360 px)
        const val VISIBLE_HOURS = 4               // default visible window

        fun roundToHour(ms: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ms
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    // ---- Data ----
    private var services: List<Service> = emptyList()
    private var epgMap: Map<String, List<EpgEvent>> = emptyMap()
    // Each entry is Triple(serviceRef, beginMs, endMs) for active/scheduled timers
    private var timerRanges: List<Triple<String, Long, Long>> = emptyList()

    /** Start of the visible time window (epoch ms, rounded to the hour) */
    var windowStartMs: Long = roundToHour(System.currentTimeMillis() - 30 * 60 * 1000L)
        set(value) {
            field = value
            invalidate()
        }

    // ---- Paints ----
    private val paintPast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.epg_past)
    }
    private val paintNow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.epg_now)
    }
    private val paintFuture = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.epg_future)
    }
    private val paintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.epg_selected)
    }
    private val paintRecording = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.epg_recording)
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_dark)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintSelectedBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 28f
    }
    private val paintSubText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 22f
    }
    private val paintNowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    // ---- Selection ----
    private var selectedRow = 0
    private var selectedCol = 0   // index within row's event list

    var onEventSelected: ((EpgEvent?) -> Unit)? = null
    var onEventClicked: ((EpgEvent, Service) -> Unit)? = null
    var onEventLongPressed: ((EpgEvent) -> Unit)? = null

    private var longPressHandled = false

    // ---- Time formatting ----
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ---- Public API ----

    fun setData(services: List<Service>, epgMap: Map<String, List<EpgEvent>>) {
        this.services = services
        this.epgMap = epgMap
        // Reset selection
        selectedRow = 0
        selectedCol = findNowEventIndex(0)
        invalidate()
        requestLayout()
    }

    /**
     * Supply active and scheduled timers so the grid can highlight matching events in red.
     * Only timers with state < 3 (i.e. Waiting/Preparing/Recording) and justPlay == 0 are used.
     */
    fun setTimers(timers: List<Timer>) {
        timerRanges = timers
            .filter { it.state < 3 && it.justPlay == 0 }
            .map { Triple(it.serviceRef, it.beginTimestamp * 1000L, it.endTimestamp * 1000L) }
        invalidate()
    }

    fun getSelectedEvent(): EpgEvent? {
        val sref = services.getOrNull(selectedRow)?.ref ?: return null
        return epgMap[sref]?.getOrNull(selectedCol)
    }

    // ---- Measurement ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val windowMinutes = VISIBLE_HOURS * 60
        val width = (windowMinutes * PIXELS_PER_MINUTE).toInt()
        val height = max(services.size * rowHeight, MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(width, height)
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nowMs = System.currentTimeMillis()

        services.forEachIndexed rowLoop@{ rowIndex, service ->
            val top = rowIndex * rowHeight.toFloat()
            val bottom = top + rowHeight
            val events = epgMap[service.ref]?.sortedBy { it.beginMs } ?: return@rowLoop

            events.forEachIndexed { colIndex, event ->
                val startX = msToPixel(max(event.beginMs, windowStartMs))
                val endX = msToPixel(event.endMs)
                if (endX < 0 || startX > width) return@forEachIndexed

                val rect = RectF(startX + eventPadding, top + eventPadding, endX - eventPadding, bottom - eventPadding)

                // Background
                val isSelected = rowIndex == selectedRow && colIndex == selectedCol
                val isTimerEvent = timerRanges.any { (ref, tBegin, tEnd) ->
                    ref == service.ref && tBegin < event.endMs && tEnd > event.beginMs
                }
                val bgPaint = when {
                    isSelected -> paintSelected
                    isTimerEvent -> paintRecording
                    event.endMs < nowMs -> paintPast
                    event.beginMs <= nowMs -> paintNow
                    else -> paintFuture
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius,
                    if (isSelected) paintSelectedBorder else paintBorder)

                // Text
                val textX = rect.left + 8f
                val titleY = top + rowHeight * 0.45f
                val timeY = top + rowHeight * 0.75f
                val clipWidth = (rect.width() - 16).toInt()
                if (clipWidth > 20) {
                    val title = ellipsize(event.title, paintText, clipWidth.toFloat())
                    canvas.drawText(title, textX, titleY, paintText)
                    val timeStr = "${timeFmt.format(Date(event.beginMs))}–${timeFmt.format(Date(event.endMs))}"
                    canvas.drawText(timeStr, textX, timeY, paintSubText)
                }
            }
        }

        // Now line
        val nowX = msToPixel(nowMs)
        if (nowX in 0f..width.toFloat()) {
            canvas.drawLine(nowX, 0f, nowX, height.toFloat(), paintNowLine)
        }
    }

    // ---- D-pad navigation ----

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedRow > 0) {
                    selectedRow--
                    selectedCol = findNowEventIndex(selectedRow)
                    notifySelection()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedRow < services.size - 1) {
                    selectedRow++
                    selectedCol = findNowEventIndex(selectedRow)
                    notifySelection()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedCol > 0) {
                    selectedCol--
                    notifySelection()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val events = eventsForRow(selectedRow)
                if (selectedCol < events.size - 1) {
                    selectedCol++
                    notifySelection()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val selectedEvent = getSelectedEvent()
                val service = services.getOrNull(selectedRow)
                if (selectedEvent != null && service != null) {
                    if (keyEvent.repeatCount >= 2 && !longPressHandled) {
                        // Long-press: trigger record
                        longPressHandled = true
                        onEventLongPressed?.invoke(selectedEvent)
                    } else if (keyEvent.repeatCount == 0) {
                        longPressHandled = false
                    }
                    if (keyEvent.repeatCount == 0 && !longPressHandled) {
                        onEventClicked?.invoke(selectedEvent, service)
                    }
                }
                true
            }
            else -> super.onKeyDown(keyCode, keyEvent)
        }
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            longPressHandled = false
        }
        return super.onKeyUp(keyCode, keyEvent)
    }

    // ---- Helpers ----

    private fun msToPixel(ms: Long): Float {
        val deltaMs = ms - windowStartMs
        return (deltaMs / 60_000f) * PIXELS_PER_MINUTE
    }

    private fun eventsForRow(row: Int): List<EpgEvent> {
        val sref = services.getOrNull(row)?.ref ?: return emptyList()
        return epgMap[sref]?.sortedBy { it.beginMs } ?: emptyList()
    }

    private fun findNowEventIndex(row: Int): Int {
        val nowMs = System.currentTimeMillis()
        val events = eventsForRow(row)
        val idx = events.indexOfFirst { it.beginMs <= nowMs && it.endMs > nowMs }
        return if (idx >= 0) idx else 0
    }

    private fun notifySelection() {
        onEventSelected?.invoke(getSelectedEvent())
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return if (end > 0) text.substring(0, end) + "…" else ""
    }
}
