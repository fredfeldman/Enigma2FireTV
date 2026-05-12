package com.enigma2.firetv.ui.bouqueteditor

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide signal that the receiver's bouquet/channel layout has changed
 * and any cached channel data should be invalidated.
 *
 * The Bouquet Editor lives in its own activity, so it can't share the main
 * screen's `ChannelViewModel`. Instead it sets [dirty] after every successful
 * server mutation, and the channels screen consumes it in `onResume`.
 */
object BouquetEditorEvents {
    private val dirty = AtomicBoolean(false)

    fun markDirty() {
        dirty.set(true)
    }

    /** Returns true exactly once after a [markDirty]. */
    fun consumeDirty(): Boolean = dirty.getAndSet(false)
}
