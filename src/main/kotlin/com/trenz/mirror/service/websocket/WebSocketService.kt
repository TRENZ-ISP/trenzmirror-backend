package com.trenz.mirror.service.websocket

import com.trenz.mirror.model.dto.WebSocketMessage
import com.trenz.mirror.service.device.DeviceService
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks live device connections and active streaming sessions, and routes
 * session-scoped messages (screen frames, input events, disconnects) between
 * the two participants of a session.
 *
 * A single instance of this class is shared by webSocketRoutes() and
 * ConnectionService so that accepting/rejecting a connection request (over
 * REST) can push a realtime notification to the device that's waiting on the
 * other end of the WebSocket.
 */
class WebSocketService(private val deviceService: DeviceService) {

    // Own scope for the grace-period offline check below - deliberately not tied to any single
    // request's lifecycle, since it needs to keep running after the WebSocket route handler that
    // triggered it has already returned.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connections = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    // sessionId -> (deviceA, deviceB). Order doesn't matter; forwardToPeer() figures out
    // which side the sender is and sends to the other one.
    private val sessionParticipants = ConcurrentHashMap<String, Pair<String, String>>()

    private companion object {
        // Grace period between a socket closing and the device actually being marked offline.
        // Covers the extremely common case of a transient disconnect (ping timeout, brief
        // network handoff) followed by the client's own automatic reconnect - without this, every
        // one of those normal blips instantly and incorrectly flipped presence to offline.
        const val OFFLINE_GRACE_PERIOD_MS = 30_000L
    }

    fun registerConnection(deviceId: String, session: DefaultWebSocketServerSession) {
        connections[deviceId] = session
        deviceService.updateDeviceStatus(deviceId, isOnline = true)
    }

    fun unregisterConnection(deviceId: String) {
        connections.remove(deviceId)
        // Clean up any sessions this device was part of so we don't leak entries
        // or keep trying to forward frames into the void.
        sessionParticipants.entries.removeIf { (_, pair) ->
            pair.first == deviceId || pair.second == deviceId
        }

        // Don't mark offline immediately - wait to see if this was just a brief drop that the
        // client's own reconnect logic resolves on its own within the grace period. Only commit
        // to "offline" if the device genuinely hasn't reconnected by the time this fires.
        scope.launch {
            delay(OFFLINE_GRACE_PERIOD_MS)
            if (!connections.containsKey(deviceId)) {
                deviceService.updateDeviceStatus(deviceId, isOnline = false)
            }
        }
    }

    fun isDeviceConnected(deviceId: String): Boolean = connections.containsKey(deviceId)

    /** Sends [message] directly to [deviceId] if it currently has a live socket. Returns whether it was delivered. */
    suspend fun sendToDevice(deviceId: String, message: WebSocketMessage): Boolean {
        val session = connections[deviceId] ?: return false
        return try {
            session.sendSerialized(message)
            true
        } catch (e: Exception) {
            // Socket died between the presence check and the send; treat as offline.
            connections.remove(deviceId)
            false
        }
    }

    /** Registers the two devices that are allowed to exchange session-scoped messages for [sessionId]. */
    fun registerSession(sessionId: String, deviceA: String, deviceB: String) {
        sessionParticipants[sessionId] = deviceA to deviceB
    }

    fun endSession(sessionId: String) {
        sessionParticipants.remove(sessionId)
    }

    fun isParticipant(sessionId: String, deviceId: String): Boolean {
        val pair = sessionParticipants[sessionId] ?: return false
        return pair.first == deviceId || pair.second == deviceId
    }

    /**
     * Forwards a session-scoped message (ScreenFrame / InputEvent / Disconnect) to whichever
     * participant of [sessionId] did NOT send it. Silently drops the message if [senderDeviceId]
     * isn't a recognized participant of that session (e.g. a stale or spoofed sessionId) -
     * this is the fix for the previous code, which tried (incorrectly) to use sendToDevice(sessionId, ...),
     * even though the connections map is keyed by deviceId, not sessionId, so nothing was ever delivered.
     */
    suspend fun forwardToPeer(senderDeviceId: String, sessionId: String, message: WebSocketMessage): Boolean {
        val participants = sessionParticipants[sessionId] ?: return false
        val peerDeviceId = when (senderDeviceId) {
            participants.first -> participants.second
            participants.second -> participants.first
            else -> return false // sender isn't part of this session - ignore
        }
        return sendToDevice(peerDeviceId, message)
    }
}
