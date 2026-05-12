package com.enigma2.firetv.ui.epgimport

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R

/**
 * Shell activity for the EPGImport viewer. Hosts [EpgImportFragment] which can
 * push [EpgImportDetailFragment] onto the back stack to show a single
 * `.sources.xml` file's contents.
 */
class EpgImportActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epgimport)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.epgimport_container, EpgImportFragment())
                .commit()
        }
    }
}
