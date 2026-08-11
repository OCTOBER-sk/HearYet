package com.hearyet.app.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Thin DataStore wrapper for session persistence (BE §10.2).
 *
 * Persists [SessionRole] and session identity so the app can attempt
 * [com.hearyet.app.transport.ControlMessage.RejoinRequest] after a process death,
 * and so the navigation graph can route correctly on cold start.
 *
 * DataStore is asynchronous, but the coordinator's call sites are synchronous;
 * reads/writes are tiny and funneled through [runBlocking]. Cleared on
 * `SessionState.Ended` or explicit leave (BE §10.2).
 */
class SessionDataStore(context: Context) {

    private val dataStore: DataStore<SessionPrefs> = sessionDataStore(context.applicationContext)

    // ── Read ─────────────────────────────────────────────────────────

    /** Returns the stored string for [key], or null if absent. */
    fun getString(key: String): String? = runBlocking {
        dataStore.data.first().values[key]
    }

    // ── Write ────────────────────────────────────────────────────────

    /** Stores [value] under [key].  Pass null to remove the key. */
    fun putString(key: String, value: String?) {
        runBlocking {
            dataStore.updateData { current ->
                val updated = if (value == null) current.values - key else current.values + (key to value)
                SessionPrefs(updated)
            }
        }
    }

    /** Clears all persisted session state. */
    fun clear() {
        runBlocking { dataStore.updateData { SessionPrefs() } }
    }

    // ── Keys ─────────────────────────────────────────────────────────

    companion object {
        /** DataStore file name (not a SharedPreferences file anymore — BE §10.2). */
        const val PREFS_NAME: String = "hearyet_session"

        private const val DATASTORE_FILE = "hearyet_session.json"

        const val KEY_SESSION_ID: String = "session_id"
        const val KEY_SESSION_CODE: String = "session_code"
        const val KEY_ROLE: String = "role"
        const val KEY_HOST_ENDPOINT_NAME: String = "host_endpoint_name"
        const val KEY_DISPLAY_NAME: String = "display_name"
        const val KEY_PREVIOUS_ENDPOINT_ID: String = "previous_endpoint_id"

        /**
         * BE §14.4.2/§17.13 — the guest identity that has already been greeted this
         * session. Persisted so a process death + RejoinRequest rejoin is never
         * re-greeted; cleared with all session state on End/Leave (§14.4.3).
         */
        const val KEY_GREETED_IDENTITY: String = "greeted_identity"

        /**
         * Single DataStore instance per process. Multiple active DataStores for the
         * same file throw IllegalStateException, and several SessionCoordinators
         * (Home + Join routes) construct this wrapper.
         */
        @Volatile
        private var instance: DataStore<SessionPrefs>? = null

        private fun sessionDataStore(context: Context): DataStore<SessionPrefs> =
            instance ?: synchronized(this) {
                instance ?: DataStoreFactory.create(
                    serializer = SessionPrefsSerializer,
                    scope = CoroutineScope(Dispatchers.IO),
                    produceFile = { context.dataStoreFile(DATASTORE_FILE) },
                ).also { instance = it }
            }
    }
}

/** Serializable map of string preferences, backed by DataStore (BE §10.2). */
@Serializable
private data class SessionPrefs(
    val values: Map<String, String> = emptyMap(),
)

private object SessionPrefsSerializer : Serializer<SessionPrefs> {

    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: SessionPrefs = SessionPrefs()

    override suspend fun readFrom(input: java.io.InputStream): SessionPrefs = try {
        json.decodeFromString(SessionPrefs.serializer(), input.readBytes().decodeToString())
    } catch (_: Exception) {
        SessionPrefs()
    }

    override suspend fun writeTo(t: SessionPrefs, output: java.io.OutputStream) {
        output.write(json.encodeToString(SessionPrefs.serializer(), t).encodeToByteArray())
    }
}
