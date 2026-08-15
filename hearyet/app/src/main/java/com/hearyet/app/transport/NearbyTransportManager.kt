package com.hearyet.app.transport

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.hearyet.app.feature.player.sync.AudioChunk
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Manages all Nearby Connections transport for HearYet sessions.
 *
 * BE §4 — Transport layer using [Strategy.P2P_STAR] with two logical
 * channels multiplexed over the same connection:
 * - **BYTES** payload for all [ControlMessage] traffic (reliable, ordered).
 * - **STREAM** payload for raw PCM [AudioChunk]s (low-latency, loss-tolerant).
 *
 * Both Host and Guest use the same manager; the role is determined by
 * which methods are called ([startAdvertising] vs. [startDiscovery]).
 */
class NearbyTransportManager(private val context: Context) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Callbacks ───────────────────────────────────────────────────

    /** Host: a remote device has connected. */
    var onEndpointConnected: ((endpointId: String, displayName: String) -> Unit)? = null

    /** Host: a remote device has disconnected. */
    var onEndpointDisconnected: ((endpointId: String) -> Unit)? = null

    /** Guest: connection to the host succeeded or failed. */
    var onConnectionResult: ((success: Boolean) -> Unit)? = null

    /** Guest: a host matching the discovery filter was found. */
    var onHostDiscovered: ((endpointId: String, endpointName: String) -> Unit)? = null

    /** Both: a [ControlMessage] was received. */
    var onControlMessage: ((endpointId: String, message: ControlMessage) -> Unit)? = null

    /** Guest: an [AudioChunk] was received on the STREAM channel. */
    var onAudioChunkReceived: ((chunk: AudioChunk) -> Unit)? = null

    /** Both: an error occurred. */
    var onError: ((message: String) -> Unit)? = null

    // ── Internal state ──────────────────────────────────────────────

    /**
     * Endpoint IDs of currently connected guests (host only). Concurrent so the
     * guest STREAM reader threads ([isConnected]) and the GMS/main thread
     * ([disconnectAll]) never race on a plain set (CR2).
     */
    private val connectedEndpoints = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * One persistent [java.io.PipedOutputStream] per guest, feeding a single
     * long-lived STREAM payload (BE §4 — "the continuous PCM audio feed", not one
     * payload per chunk). Opened on connect, closed on disconnect.
     *
     * FIX 2c (CR2) — ConcurrentHashMap: written/read by the per-guest sender
     * threads ([sendAudioChunk]) and removed by the main/GMS thread
     * ([closeAudioStream], [disconnectAll]); a plain map was a data race.
     */
    private val guestAudioStreams = java.util.concurrent.ConcurrentHashMap<String, java.io.PipedOutputStream>()

    /**
     * Endpoint IDs with an active continuous-STREAM reader loop. Guards against
     * spawning a duplicate reader for the same endpoint.
     */
    private val guestAudioReaders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * FIX 2b (R3) — the active reader's [java.io.InputStream] per endpoint, so a
     * duplicate STREAM payload can close the stale stream and force the old reader
     * to exit instead of staying permanently deaf on a dead stream.
     */
    private val guestAudioReaderStreams = java.util.concurrent.ConcurrentHashMap<String, java.io.InputStream>()

    /** Read-only snapshot of connected endpoint IDs (C11 — guest list UI). */
    val connectedEndpointIds: Set<String> get() = connectedEndpoints.toSet()

    /** The host endpoint id the guest is connected to (guest only). */
    @Volatile
    var hostEndpointId: String? = null
        private set

    private var isAdvertising: Boolean = false
    private var isDiscovering: Boolean = false

    /**
     * True when this device is the Host (set by [startAdvertising]).
     * False when this device is the Guest (set by [startDiscovery]).
     *
     * Used in [connectionLifecycleCallback] to dispatch the correct
     * per-role callback on a successful connection (BE §4).
     */
    private var isHostMode: Boolean = false

    /**
     * Temporary staging map: endpointId → displayName from [ConnectionInfo.endpointName].
     * Populated in [onConnectionInitiated]; consumed and cleared in [onConnectionResult].
     * Needed because [ConnectionInfo] is only available in [onConnectionInitiated], not later.
     */
    private val stagedDisplayNames = mutableMapOf<String, String>()

    // ── Service ID ──────────────────────────────────────────────────
    companion object {
        /** Fixed service ID for HearYet. All instances must use this. */
        const val SERVICE_ID = "com.hearyet.app.nearby"

        /** Internal pipe buffer for each guest's continuous STREAM payload (~17 frames of 3840B). */
        private const val PIPE_BUFFER_BYTES = 65_536

        /**
         * FIX 2b (R3) — how long a duplicate STREAM payload waits for the previous
         * reader to observe EOF/close and release the reader guard before giving up
         * (dropping the duplicate rather than running two readers for one endpoint).
         */
        private const val READER_HANDOVER_TIMEOUT_MS = 2_000L

        private const val TAG = "NearbyTransportMgr"
    }

    // ── Error reporting ───────────────────────────────────────────

    /**
     * Build a failure message that preserves the underlying status code when
     * the failure is an [ApiException] (the common case for Nearby Task
     * failures). The status code/text is what tells a real device apart from
     * an unsupported one, so it must not be discarded before the coordinator
     * surfaces it to the user.
     */
    private fun describeFailure(action: String, e: Exception): String {
        val api = e as? ApiException
        return if (api != null && api.statusCode != 0) {
            "$action failed (Nearby status ${api.statusCode}: ${api.statusMessage ?: e.message})"
        } else {
            "$action failed: ${e.message}"
        }
    }

    // ── Host: Advertising ───────────────────────────────────────────

    /**
     * Start advertising this device as a HearYet host.
     * @param endpointName Unique name for this session (should embed [SessionPayload.sessionCode]).
     */
    fun startAdvertising(endpointName: String) {
        if (isAdvertising) return
        Log.d(TAG, "startAdvertising: $endpointName")

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        client.startAdvertising(
            /* name = */ endpointName,
            /* serviceId = */ SERVICE_ID,
            /* callback = */ connectionLifecycleCallback,
            /* options = */ options,
        ).addOnSuccessListener {
            isAdvertising = true
            isHostMode = true
            Log.d(TAG, "Advertising started: $endpointName")
        }.addOnFailureListener { e ->
            Log.e(TAG, "startAdvertising failed", e)
            onError?.invoke(describeFailure("startAdvertising", e))
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        Log.d(TAG, "stopAdvertising")
        client.stopAdvertising()
        isAdvertising = false
    }

    // ── Guest: Discovery & connection ────────────────────────────────

    /**
     * Start discovering nearby HearYet hosts.
     * [onHostDiscovered] will fire for each matching endpoint found.
     */
    fun startDiscovery() {
        if (isDiscovering) return
        Log.d(TAG, "startDiscovery")

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        client.startDiscovery(
            /* serviceId = */ SERVICE_ID,
            /* callback = */ object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(
                    endpointId: String,
                    info: com.google.android.gms.nearby.connection.DiscoveredEndpointInfo,
                ) {
                    Log.d(TAG, "Endpoint found: $endpointId name=${info.endpointName}")
                    onHostDiscovered?.invoke(endpointId, info.endpointName)
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d(TAG, "Endpoint lost: $endpointId")
                }
            },
            /* options = */ options,
        ).addOnSuccessListener {
            isDiscovering = true
            isHostMode = false
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "startDiscovery failed", e)
            onError?.invoke(describeFailure("startDiscovery", e))
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        Log.d(TAG, "stopDiscovery")
        client.stopDiscovery()
        isDiscovering = false
    }

    /**
     * Request a connection to a discovered host. Fires [onConnectionResult].
     */
    fun requestConnection(hostEndpointId: String, localEndpointName: String) {
        Log.d(TAG, "requestConnection: $hostEndpointId as $localEndpointName")
        client.requestConnection(
            /* name = */ localEndpointName,
            /* endpointId = */ hostEndpointId,
            /* callback = */ connectionLifecycleCallback,
        ).addOnSuccessListener {
            // Connection request sent; result arrives via connectionLifecycleCallback
            Log.d(TAG, "Connection request sent to $hostEndpointId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "requestConnection failed", e)
            onConnectionResult?.invoke(false)
            onError?.invoke(describeFailure("Connection request", e))
        }
    }

    // ── Messaging ───────────────────────────────────────────────────

    /** Send a [ControlMessage] to a specific endpoint (BYTES channel). */
    fun sendControlMessage(endpointId: String, message: ControlMessage) {
        val bytes = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        client.sendPayload(endpointId, payload)
        Log.d(TAG, "sendControlMessage to $endpointId: ${message::class.simpleName}")
    }

    /** Send an [AudioChunk] to [endpointId] over its already-open continuous STREAM
     *  payload (BE §4/§6). Must call [openAudioStream] first. Writes are blocking on
     *  the pipe's internal buffer — call from the per-guest sender thread
     *  (GuestOutboundQueue's consumer), never from the main thread.
     *
     *  @return true when the frame was accepted; false when the stream is missing or
     *  died (the dead stream is closed and removed so the caller can re-open it). */
    fun sendAudioChunk(endpointId: String, chunk: AudioChunk): Boolean {
        val out = guestAudioStreams[endpointId] ?: run {
            Log.w(TAG, "sendAudioChunk: no open stream for $endpointId — did openAudioStream run?")
            return false
        }
        return try {
            AudioChunk.writeFramedChunk(out, chunk)
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendAudioChunk: stream died for $endpointId — removing for recovery re-open", e)
            try {
                out.close()
            } catch (_: Exception) {
            }
            guestAudioStreams.remove(endpointId)
            false
        }
    }

    /** Open the single continuous STREAM payload for [endpointId]'s audio feed.
     *  Call once, right after the guest connects (Host side) — before any
     *  [sendAudioChunk] calls for that endpoint. */
    fun openAudioStream(endpointId: String) {
        if (guestAudioStreams.containsKey(endpointId)) return
        val pipedOut = java.io.PipedOutputStream()
        val pipedIn = java.io.PipedInputStream(pipedOut, PIPE_BUFFER_BYTES)
        guestAudioStreams[endpointId] = pipedOut
        val payload = Payload.fromStream(pipedIn)
        client.sendPayload(endpointId, payload)
        Log.d(TAG, "openAudioStream: opened continuous STREAM payload for $endpointId")
    }

    /** Close and forget [endpointId]'s continuous stream (on disconnect). */
    fun closeAudioStream(endpointId: String) {
        guestAudioStreams.remove(endpointId)?.let {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Broadcast a [ControlMessage] to all connected endpoints. */
    fun broadcastControlMessage(message: ControlMessage) {
        val bytes = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        client.sendPayload(connectedEndpoints.toList(), payload)
        Log.d(TAG, "broadcastControlMessage: ${message::class.simpleName} to ${connectedEndpoints.size} endpoints")
    }

    // ── Disconnection ───────────────────────────────────────────────

    fun disconnect(endpointId: String) {
        Log.d(TAG, "disconnect: $endpointId")
        client.disconnectFromEndpoint(endpointId)
        connectedEndpoints.remove(endpointId)
        closeAudioStream(endpointId)
        if (endpointId == hostEndpointId) hostEndpointId = null
    }

    fun disconnectAll() {
        Log.d(TAG, "disconnectAll: ${connectedEndpoints.size} endpoints")
        if (hostEndpointId != null) {
            client.disconnectFromEndpoint(hostEndpointId!!)
        }
        connectedEndpoints.forEach { client.disconnectFromEndpoint(it) }
        connectedEndpoints.clear()
        // Close every continuous STREAM payload deterministically during teardown
        // (not only via per-endpoint onDisconnected callbacks).
        guestAudioStreams.forEach { (_, out) ->
            try {
                out.close()
            } catch (_: Exception) {
            }
        }
        guestAudioStreams.clear()
        // FIX 2b — close any active guest STREAM reader streams so blocked readers
        // observe EOF and exit instead of lingering after teardown.
        guestAudioReaderStreams.forEach { (_, input) ->
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
        guestAudioReaderStreams.clear()
        guestAudioReaders.clear()
        hostEndpointId = null
    }

    // ── Connection lifecycle ────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated: $endpointId name=${info.endpointName}")
            // Stage the display name now — ConnectionInfo is only available here, not in onConnectionResult.
            stagedDisplayNames[endpointId] = info.endpointName
            // Auto-accept all connections in P2P_STAR.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                Log.d(TAG, "onConnectionResult: $endpointId SUCCESS (isHostMode=$isHostMode)")
                connectedEndpoints.add(endpointId)

                if (isHostMode) {
                    // Host: a guest successfully connected — fire the host-specific callback.
                    // Consume and remove the staged display name; fall back to endpointId if missing.
                    val displayName = stagedDisplayNames.remove(endpointId) ?: endpointId
                    onEndpointConnected?.invoke(endpointId, displayName)
                } else {
                    // Guest: successfully connected to the host.
                    hostEndpointId = endpointId
                    stagedDisplayNames.remove(endpointId) // clean up; not needed on guest side
                    onConnectionResult?.invoke(true)
                }
            } else {
                Log.w(TAG, "onConnectionResult: $endpointId FAILED ${resolution.status}")
                stagedDisplayNames.remove(endpointId)
                onConnectionResult?.invoke(false)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "onDisconnected: $endpointId")
            connectedEndpoints.remove(endpointId)
            if (endpointId == hostEndpointId) hostEndpointId = null
            onEndpointDisconnected?.invoke(endpointId)
        }
    }

    // ── Payload (data) handling ─────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytesPayload(endpointId, payload)
                Payload.Type.STREAM -> handleStreamPayload(endpointId, payload)
                else -> Log.w(TAG, "Unexpected payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Status updates for outgoing payloads — mostly informational.
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.w(TAG, "Payload transfer failed to $endpointId")
            }
        }
    }

    private fun handleBytesPayload(endpointId: String, payload: Payload) {
        val bytes = payload.asBytes() ?: return
        val text = String(bytes, Charsets.UTF_8)
        try {
            val message = json.decodeFromString<ControlMessage>(text)
            Log.d(TAG, "ControlMessage from $endpointId: ${message::class.simpleName}")
            onControlMessage?.invoke(endpointId, message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode ControlMessage from $endpointId", e)
        }
    }

    private fun handleStreamPayload(endpointId: String, payload: Payload) {
        val stream = payload.asStream() ?: return
        val input = stream.asInputStream()

        // FIX 2b (R3) — allow reader replacement: a second STREAM payload for an
        // endpoint usually means the host re-opened the stream after a failure. The
        // old continuous stream may have died silently (no EOF ever delivered), so
        // close it to force the stale reader out instead of staying deaf forever.
        if (guestAudioReaders.contains(endpointId)) {
            Log.w(TAG, "handleStreamPayload: replacing active reader for $endpointId")
            guestAudioReaderStreams.remove(endpointId)?.let { oldInput ->
                try {
                    oldInput.close()
                } catch (_: Exception) {
                }
            }
            // Give the old reader time to observe EOF/close and release the guard.
            // If it never does (close didn't propagate), fall through and drop the
            // duplicate rather than run two readers for one endpoint.
            val deadline = System.currentTimeMillis() + READER_HANDOVER_TIMEOUT_MS
            while (guestAudioReaders.contains(endpointId) && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }

        if (!guestAudioReaders.add(endpointId)) {
            Log.w(TAG, "handleStreamPayload: reader still active for $endpointId — ignoring duplicate payload")
            return
        }
        guestAudioReaderStreams[endpointId] = input
        // One dedicated reader thread per endpoint: the continuous STREAM stays open
        // for the life of the connection, so a shared executor would let one stalled
        // guest block every other guest's audio (head-of-line blocking).
        Thread({
            try {
                // Length-prefixed records arrive one after another on the same stream
                // ([4B length][24B header][PCM]); null = stream closed / EOF / malformed.
                while (isConnected(endpointId)) {
                    val chunk = AudioChunk.readFramedChunk(input) ?: break
                    onAudioChunkReceived?.invoke(chunk)
                }
            } catch (e: Exception) {
                Log.w(TAG, "STREAM payload reader stopped for $endpointId", e)
            } finally {
                guestAudioReaders.remove(endpointId)
                // Only remove the entry if it still refers to THIS reader's stream —
                // a replacement reader for the same endpoint must not be cleared.
                guestAudioReaderStreams.remove(endpointId, input)
            }
            Log.d(TAG, "STREAM payload reader exiting for $endpointId")
        }, "HearYet-StreamReader-$endpointId").apply {
            isDaemon = true
            start()
        }
    }

    // ── Query helpers ───────────────────────────────────────────────

    val connectedEndpointCount: Int get() = connectedEndpoints.size
    fun isConnected(endpointId: String): Boolean = endpointId in connectedEndpoints
    fun isHostConnected(): Boolean = hostEndpointId != null
}
