package com.hearyet.app.core.model

/**
 * Process-level holder for the active [SessionHandle].
 *
 * Set by the HomeRoute Create flow before launching PlayerActivity.
 * Read by PlayerActivity and any other UI that needs session state.
 * Lives in `:core:model` so both `:app` and `:feature:player` can access it.
 *
 * C11 — In-session Host UI (FE §9.6).
 */
object SessionHolder {
    @Volatile
    var active: SessionHandle? = null
}
