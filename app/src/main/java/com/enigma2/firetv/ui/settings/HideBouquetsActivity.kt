package com.enigma2.firetv.ui.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R

class HideBouquetsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hide_bouquets)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.hide_bouquets_container, HideBouquetsFragment())
                .commit()
        }
    }
}
