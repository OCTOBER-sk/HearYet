package com.hearyet.app.sync

/**
 * Session role, decided at runtime (BE §0 — Host and Guest run from the same APK).
 *
 * `SessionState`, `SessionError`, `SyncHealth`, and `GuestInfo` live in
 * `:core:model` (single authoritative copy) so `:core:ui` / `:feature:player`
 * can consume them without depending on `:app`.
 */
sealed class SessionRole {
    data object Host : SessionRole()
    data object Guest : SessionRole()
}
