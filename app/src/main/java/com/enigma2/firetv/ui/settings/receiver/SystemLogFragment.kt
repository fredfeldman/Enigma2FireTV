package com.enigma2.firetv.ui.settings.receiver

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phase 1.5.11 — System log viewer with filter + Share-to-Downloads. */
class SystemLogFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_log_title

    private var fullLog: String = ""
    private lateinit var filter: EditText
    private lateinit var logView: TextView

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_refresh) { refresh() }
        addActionButton(R.string.rs_log_share) { share() }

        filter = EditText(requireContext()).apply {
            hint = getString(R.string.rs_log_filter_hint)
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        bodyView.addView(filter)
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        logView = makeBodyText("", mono = true)
        bodyView.addView(logView)
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        setStatus(getString(R.string.rs_storage_loading))
        fullLog = repo.getReceiverLog() ?: ""
        setStatus("")
        applyFilter()
    }

    private fun applyFilter() {
        val needle = filter.text.toString().trim()
        logView.text = if (needle.isEmpty()) fullLog
        else fullLog.lineSequence()
            .filter { it.contains(needle, ignoreCase = true) }
            .joinToString("\n")
    }

    private fun share() = lifecycleScope.launch {
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val name = "enigma2firetv_log_$ts.txt"
        val ok = withContext(Dispatchers.IO) { writeToDownloads(requireContext(), name, fullLog) }
        val msg: String = if (ok) getString(R.string.rs_log_saved_fmt, name)
                          else getString(R.string.rs_action_failed)
        toast(msg)
    }

    private fun writeToDownloads(ctx: Context, fileName: String, content: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { it.write(content.toByteArray()) }
                true
            }
        }.getOrDefault(false)
    }
}
