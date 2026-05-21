package com.enigma2.firetv.ui.settings.receiver

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.enigma2.firetv.R
import com.enigma2.firetv.data.repository.Enigma2Repository

/**
 * Base class for every Receiver Settings sub-screen. Provides a standard
 * scrollable layout with title / status TextView / actions row / body container,
 * plus convenience builders so the concrete screens stay tiny.
 */
abstract class ReceiverSettingsBaseFragment : Fragment(R.layout.fragment_receiver_settings_screen) {

    protected val repo = Enigma2Repository()

    protected lateinit var titleView: TextView
    protected lateinit var statusView: TextView
    protected lateinit var actionsRow: LinearLayout
    protected lateinit var bodyView: LinearLayout

    @get:StringRes protected abstract val screenTitleRes: Int

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        titleView = view.findViewById(R.id.tv_title)
        statusView = view.findViewById(R.id.tv_status)
        actionsRow = view.findViewById(R.id.ll_actions)
        bodyView = view.findViewById(R.id.ll_body)
        titleView.setText(screenTitleRes)
        onScreenReady(view, savedInstanceState)
    }

    protected abstract fun onScreenReady(view: View, savedInstanceState: Bundle?)

    // ── shared UI helpers ───────────────────────────────────────────────

    protected fun addActionButton(@StringRes label: Int, onClick: () -> Unit): Button {
        val ctx = requireContext()
        val b = Button(ctx).apply {
            setText(label)
            setBackgroundResource(R.drawable.bg_button)
            setTextColor(Color.WHITE)
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = 12
        actionsRow.addView(b, lp)
        return b
    }

    protected fun makeBodyText(text: String, mono: Boolean = false): TextView {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        if (mono) tv.typeface = android.graphics.Typeface.MONOSPACE
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 8
        tv.layoutParams = lp
        return tv
    }

    protected fun setStatus(text: String) {
        statusView.text = text
    }

    protected fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    protected fun toastRes(@StringRes resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    protected fun toastOkOrFail(success: Boolean) {
        toastRes(if (success) R.string.rs_action_ok else R.string.rs_action_failed)
    }

    protected fun row(): LinearLayout {
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 8
        ll.layoutParams = lp
        return ll
    }

    companion object {
        @JvmStatic
        fun dp(ctx: Context, value: Int): Int =
            (value * ctx.resources.displayMetrics.density).toInt()
    }
}
