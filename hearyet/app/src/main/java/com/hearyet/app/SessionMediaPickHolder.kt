package com.hearyet.app

import android.net.Uri

/**
 * Bridges the Create-session sheet (owned by [com.hearyet.app.navigation.HearYetNavGraph]'s
 * HomeRoute) and the media library picker (owned by MediaNavGraph).
 *
 * When the sheet's "Pick a file" is tapped, HomeRoute installs [pendingPick] and
 * navigates to the library. The library's play action is intercepted in
 * MediaNavGraph and routed back through this holder; the picked URI is stashed
 * in [pickedUri] so HomeRoute can adopt it after returning to the back stack.
 *
 * Process-local singleton, deliberately not persisted — a session that dies
 * with the process has no media selection to restore.
 */
object SessionMediaPickHolder {

    @Volatile
    var pendingPick: ((Uri) -> Unit)? = null

    @Volatile
    var pickedUri: Uri? = null

    /** Returns and clears the pending pick callback, if any. */
    fun consume(): ((Uri) -> Unit)? {
        val callback = pendingPick
        pendingPick = null
        return callback
    }

    /** Returns and clears the stashed picked URI, if any. */
    fun consumePickedUri(): Uri? {
        val uri = pickedUri
        pickedUri = null
        return uri
    }
}
