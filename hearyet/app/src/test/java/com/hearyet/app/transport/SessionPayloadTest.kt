package com.hearyet.app.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BE §3/§4 — the endpoint-name contract that makes discovery collision-safe:
 * the host advertises a prefix-prefixed name embedding the session code, and
 * the guest matches on exact name AND prefix before ever connecting.
 */
class SessionPayloadTest {

    @Test
    fun buildEndpointName_embedsTheSessionCode() {
        assertEquals("HearYet-7H3K9P", SessionPayload.buildEndpointName("7H3K9P"))
    }

    @Test
    fun isMatchingEndpoint_acceptsExactPrefixedName() {
        val expected = SessionPayload.buildEndpointName("7H3K9P")

        assertTrue(
            SessionPayload.isMatchingEndpoint("HearYet-7H3K9P", expected),
        )
    }

    @Test
    fun isMatchingEndpoint_rejectsDifferentSessionCode() {
        val expected = SessionPayload.buildEndpointName("7H3K9P")

        assertFalse(
            SessionPayload.isMatchingEndpoint("HearYet-ABC123", expected),
        )
    }

    @Test
    fun isMatchingEndpoint_rejectsBareNameWithoutPrefix() {
        // Equal strings but no HearYet- prefix — a bare endpoint name is never
        // a valid match, even if it equals the expected value (prefix guard).
        assertFalse(
            SessionPayload.isMatchingEndpoint("7H3K9P", "7H3K9P"),
        )
    }

    @Test
    fun isMatchingEndpoint_rejectsCaseVariant() {
        val expected = SessionPayload.buildEndpointName("7H3K9P")

        assertFalse(
            SessionPayload.isMatchingEndpoint("hearyet-7H3K9P", expected),
        )
    }
}
