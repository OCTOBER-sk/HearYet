package com.hearyet.app.core.model

/**
 * Per-guest session information for the Host's in-session UI.
 *
 * C11 — In-session Host side sheet guest list (FE §9.6).
 * Duplicated from [com.hearyet.app.sync.GuestInfo] so `:feature:player`
 * can access it without depending on `:app`.
 */
data class GuestInfo(
    val endpointId: String,
    val displayName: String,
    val clockOffsetMs: Double,
    val lastRttMs: Long,
    val driftMs: Double,
    val syncHealth: SyncHealth,
    val connectedAtMs: Long,
)
