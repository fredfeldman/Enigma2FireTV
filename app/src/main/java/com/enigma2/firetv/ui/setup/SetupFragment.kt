package com.enigma2.firetv.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.ui.main.MainActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * First-run screen: collects receiver IP, port and credentials,
 * tests the connection, then navigates to [com.enigma2.firetv.ui.channels.ChannelsFragment].
 */
class SetupFragment : Fragment() {

    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cbHttps: CheckBox
    private lateinit var btnConnect: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvStatus: TextView

    private lateinit var prefs: ReceiverPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        etHost = view.findViewById(R.id.et_host)
        etPort = view.findViewById(R.id.et_port)
        etUsername = view.findViewById(R.id.et_username)
        etPassword = view.findViewById(R.id.et_password)
        cbHttps = view.findViewById(R.id.cb_https)
        btnConnect = view.findViewById(R.id.btn_connect)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvStatus = view.findViewById(R.id.tv_status)

        // Pre-fill from prefs
        if (prefs.host.isNotBlank()) {
            etHost.setText(prefs.host)
            etPort.setText(prefs.port.toString())
            etUsername.setText(prefs.username)
            etPassword.setText(prefs.password)
            cbHttps.isChecked = prefs.useHttps
        } else {
            etPort.setText("80")
        }

        btnConnect.setOnClickListener { attemptConnect() }
    }

    private fun attemptConnect() {
        val host = etHost.text?.toString()?.trim() ?: ""
        val portStr = etPort.text?.toString()?.trim() ?: "80"
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val useHttps = cbHttps.isChecked

        if (host.isBlank()) {
            showStatus(getString(R.string.hint_receiver_ip) + " is required", isError = true)
            return
        }

        val port = portStr.toIntOrNull() ?: 80

        setLoading(true)
        showStatus("", isError = false)

        // Initialize API client and test connection
        ApiClient.initialize(host, port, useHttps, username, password)

        viewLifecycleOwner.lifecycleScope.launch {
            val repo = Enigma2Repository()
            val bouquets = try {
                repo.getBouquets()
            } catch (e: Exception) {
                android.util.Log.e("SetupFragment", "Connection failed", e)
                setLoading(false)
                val msg = e.message ?: e.javaClass.simpleName
                showStatus("Error: $msg", isError = true)
                return@launch
            }
            setLoading(false)

            if (bouquets.isNotEmpty()) {
                // Save prefs
                prefs.host = host
                prefs.port = port
                prefs.useHttps = useHttps
                prefs.username = username
                prefs.password = password

                (activity as? MainActivity)?.showChannels()
            } else {
                showStatus(getString(R.string.error_connection), isError = true)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        btnConnect.isEnabled = !loading
    }

    private fun showStatus(message: String, isError: Boolean) {
        if (message.isEmpty()) {
            tvStatus.visibility = View.GONE
        } else {
            tvStatus.text = message
            tvStatus.setTextColor(
                if (isError) resources.getColor(R.color.error, null)
                else resources.getColor(R.color.accent, null)
            )
            tvStatus.visibility = View.VISIBLE
        }
    }
}
