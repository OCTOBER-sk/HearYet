package com.hearyet.app.transport

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object SessionPayloadCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: SessionPayload): String =
        Base64.encodeToString(
            json.encodeToString(payload).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )

    fun decode(raw: String): SessionPayload? = try {
        val decoded = Base64.decode(raw, Base64.NO_WRAP)
        json.decodeFromString(String(decoded, Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    fun generateSessionCode(): String {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
