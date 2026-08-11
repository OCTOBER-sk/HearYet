package com.hearyet.app.core.ui.component

import com.hearyet.app.core.model.SessionError

/**
 * FE §9.11 — the user-facing message for a [SessionError].
 *
 * Single source of truth shared by the Join flow's ErrorView and the Create
 * sheet's host-side error banner, so the two screens can never drift out of
 * sync about what a given error means.
 */
fun sessionErrorMessage(reason: SessionError?): String = when (reason) {
    SessionError.QR_INVALID -> "That doesn't look like a HearYet code."
    SessionError.PAYLOAD_INVALID -> "This session code isn't compatible with this version of HearYet."
    SessionError.CONNECTION_FAILED -> "Couldn't connect to the host."
    SessionError.DISCOVERY_FAILED -> "Couldn't find the host nearby."
    SessionError.DEVICE_INCOMPATIBLE -> "This device doesn't support the connection HearYet needs."
    SessionError.SYNC_TIMEOUT -> "Couldn't get a stable sync with the host."
    SessionError.HOST_UNREACHABLE -> "The host is no longer responding."
    SessionError.PERMISSION_MISSING -> "HearYet needs camera and Bluetooth access to join a session."
    null -> "Something went wrong."
}
