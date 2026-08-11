package com.hearyet.app.core.model

/**
 * Lets `:feature:player` start a Host session without depending on `:app` (BE §2.1).
 * The app's [android.app.Application] implements this so the in-player session button
 * can turn whatever is currently playing locally into a live Host session.
 */
interface SessionStartProvider {
    /** Begin hosting a new session with the given display name (BE §2.1). */
    fun startHostSession(displayName: String)
}
