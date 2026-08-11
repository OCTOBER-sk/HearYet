package com.hearyet.app.transport

import kotlinx.serialization.Serializable

@Serializable
data class SessionPayload(
    val sessionId: String,
    val sessionCode: String,
    val hostEndpointName: String,
    val hostDisplayName: String,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
) {
    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1

        /** Prefix for endpoint names so they are easily filterable. */
        private const val ENDPOINT_PREFIX = "HearYet-"

        /**
         * Build the unique endpoint name that the Host advertises.  The session
         * code is embedded so two concurrent hosts never collide (BE §4).
         */
        fun buildEndpointName(sessionCode: String): String = "$ENDPOINT_PREFIX$sessionCode"

        /**
         * BE §4 discovery rule: never match on bare endpoint name alone.
         * A discovered endpoint is a valid HearYet host only if its name
         * starts with [ENDPOINT_PREFIX] and exactly matches the expected
         * [hostEndpointName] from the QR payload.
         */
        fun isMatchingEndpoint(
            discoveredEndpointName: String,
            expectedHostEndpointName: String,
        ): Boolean = discoveredEndpointName == expectedHostEndpointName &&
            discoveredEndpointName.startsWith(ENDPOINT_PREFIX)
    }
}
