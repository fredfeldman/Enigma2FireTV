package com.enigma2.firetv.ui.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R

class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}
