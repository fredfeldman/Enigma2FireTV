package com.enigma2.firetv.ui.bouqueteditor

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R

/**
 * Shell activity for the Bouquet Editor. Hosts [BouquetEditorFragment] which can
 * push [BouquetEditFragment] and [AddServicePickerFragment] onto the back stack.
 */
class BouquetEditorActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bouquet_editor)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.bouquet_editor_container, BouquetEditorFragment())
                .commit()
        }
    }
}
