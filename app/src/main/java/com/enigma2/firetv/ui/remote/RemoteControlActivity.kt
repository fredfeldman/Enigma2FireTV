package com.enigma2.firetv.ui.remote

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

/**
 * Virtual remote control. Each button posts an Enigma2 keycode to
 * `/api/remotecontrol?command=<code>`. Codes are the standard Linux
 * input event numbers used by Enigma2's `eRCInput` driver — kept in
 * lockstep with the sibling Enigma2Android v1.5.1 RemoteControlActivity.
 *
 * Initial focus lands on the centre OK button so the D-pad can drive
 * the whole grid without ever touching the screen.
 */
class RemoteControlActivity : FragmentActivity() {

    private val repo = Enigma2Repository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)

        // Standard Enigma2 / Linux input keycodes — same table as Android sibling.
        val mapping = mapOf(
            R.id.btn_up to 103, R.id.btn_down to 108, R.id.btn_left to 105, R.id.btn_right to 106,
            R.id.btn_ok to 352, R.id.btn_back to 412, R.id.btn_exit to 174, R.id.btn_menu to 139,
            R.id.btn_info to 358, R.id.btn_epg to 365, R.id.btn_text to 388, R.id.btn_help to 138,
            R.id.btn_audio to 392, R.id.btn_subs to 370, R.id.btn_power to 116, R.id.btn_mute to 113,
            R.id.btn_vol_up to 115, R.id.btn_vol_down to 114,
            R.id.btn_ch_up to 402, R.id.btn_ch_down to 403,
            R.id.btn_play to 207, R.id.btn_pause to 119, R.id.btn_stop to 128, R.id.btn_record to 167,
            R.id.btn_red to 398, R.id.btn_green to 399, R.id.btn_yellow to 400, R.id.btn_blue to 401,
            R.id.btn_0 to 11, R.id.btn_1 to 2, R.id.btn_2 to 3, R.id.btn_3 to 4, R.id.btn_4 to 5,
            R.id.btn_5 to 6, R.id.btn_6 to 7, R.id.btn_7 to 8, R.id.btn_8 to 9, R.id.btn_9 to 10
        )

        val listener = View.OnClickListener { v ->
            val code = mapping[v.id] ?: return@OnClickListener
            send(code)
        }
        mapping.keys.forEach { id -> findViewById<Button>(id)?.setOnClickListener(listener) }

        // Start focus on OK so D-pad navigation has a natural anchor.
        findViewById<Button>(R.id.btn_ok)?.post {
            findViewById<Button>(R.id.btn_ok)?.requestFocus()
        }
    }

    private fun send(code: Int) {
        lifecycleScope.launch {
            val ok = repo.sendRemoteCommand(code)
            if (!ok) Toast.makeText(this@RemoteControlActivity,
                R.string.remote_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
