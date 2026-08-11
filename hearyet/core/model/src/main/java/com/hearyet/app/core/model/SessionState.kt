package com.hearyet.app.core.model

/**
 * Session model types shared by UI components and the session runtime.
 *
 * These types live here in `:core:model` (single authoritative copy) so
 * `:core:ui` and `:feature:player` can consume them without depending on
 * `:app`. The `:app` module's `sync/SessionModels.kt` defines only the
 * runtime-role types that stay app-scoped (`SessionRole`) and references
 * these definitions.
 *
 * Keep this file in sync with the backend doc's model names — never rename
 * or shadow a session vocabulary term in UI code (FE §0 terminology lock).
 */
sealed class SessionState {
    data object Idle : SessionState()
    data object Advertising : SessionState()
    data object WaitingForMedia : SessionState()
    data object Discovering : SessionState()
    data object ClockSyncing : SessionState()
    data class Connected(val guestCount: Int) : SessionState()
    data class Playing(val positionMs: Long) : SessionState()

    /**
     * [reason] is the user-facing category; [detail] carries the underlying
     * technical cause when one is known (e.g. the Nearby/Play-services status
     * text) so the UI never has to guess why a generic error fired.
     */
    data class Error(val reason: SessionError, val detail: String? = null) : SessionState()
    data object Ended : SessionState()
}

enum class SessionError {
    PERMISSION_MISSING,
    CONNECTION_FAILED,
    QR_INVALID,
    PAYLOAD_INVALID,
    DISCOVERY_FAILED,
    DEVICE_INCOMPATIBLE,
    SYNC_TIMEOUT,
    HOST_UNREACHABLE,
}

enum class SyncHealth {
    GOOD,
    DEGRADED,
    POOR,
}
