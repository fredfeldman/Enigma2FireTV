package com.enigma2.firetv.ui.epg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.enigma2.firetv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Horizontal ruler showing time markers for the EPG grid.
 * Should be aligned with [EpgGridView] via a shared [windowStartMs].
 */
class EpgTimeRulerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var windowStartMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 26f
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_elevated)
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (windowStartMs == 0L) return

        // Draw a tick every 30 minutes
        val intervalMs = 30 * 60 * 1000L
        var tickMs = windowStartMs
        val windowEndMs = windowStartMs + (EpgGridView.VISIBLE_HOURS * 60 * 60 * 1000L)

        while (tickMs <= windowEndMs) {
            val x = ((tickMs - windowStartMs) / 60_000f) * EpgGridView.PIXELS_PER_MINUTE
            val label = timeFmt.format(Date(tickMs))
            canvas.drawText(label, x + 4f, height * 0.7f, paintText)
            canvas.drawLine(x, 0f, x, height.toFloat(), paintLine)
            tickMs += intervalMs
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (EpgGridView.VISIBLE_HOURS * 60 * EpgGridView.PIXELS_PER_MINUTE).toInt()
        val h = MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: 48
        setMeasuredDimension(w, h)
    }
}
