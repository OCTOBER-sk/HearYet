package com.hearyet.app.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-level holder for the active [SessionHandle].
 *
 * Set by the HomeRoute Create flow before launching PlayerActivity.
 * Read by PlayerActivity and any other UI that needs session state.
 * Lives in `:core:model` so both `:app` and `:feature:player` can access it.
 *
 * C11 — In-session Host UI (FE §9.6).
 *
 * The active handle is backed by a [StateFlow] (thread-safe by construction —
 * its value is stored volatily) so collectors can react to a session being
 * created *after* their collection started, and to a handle replacement
 * mid-flight (M-7), via [activeFlow].
 */
object SessionHolder {
    private val mutableActiveFlow = MutableStateFlow<SessionHandle?>(null)

    /** Observable stream of the active [SessionHandle] (M-7). */
    val activeFlow: StateFlow<SessionHandle?> = mutableActiveFlow

    var active: SessionHandle?
        get() = mutableActiveFlow.value
        set(value) {
            mutableActiveFlow.value = value
        }
}
