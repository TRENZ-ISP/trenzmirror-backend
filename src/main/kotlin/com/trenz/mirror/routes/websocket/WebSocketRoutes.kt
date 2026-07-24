package com.trenz.mirror.routes.websocket

import com.trenz.mirror.model.dto.WebSocketMessage
import com.trenz.mirror.service.auth.JwtService
import com.trenz.mirror.service.device.DeviceService
import com.trenz.mirror.service.websocket.WebSocketService
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes(
    webSocketService: WebSocketService,
    jwtService: JwtService,
    deviceService: DeviceService
) {
    webSocket("/ws") {
        var authenticatedDeviceId: String? = null

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue

                val text = frame.readText()
                val message = try {
                    Json.decodeFromString(WebSocketMessage.serializer(), text)
                } catch (e: SerializationException) {
                    continue // malformed frame - ignore rather than crash the socket
                }

                // Require authentication before anything else is processed. This closes the
                // previous gap where a client could send ScreenFrame/InputEvent for *any*
                // deviceId/sessionId with no proof of identity at all.
                if (authenticatedDeviceId == null) {
                    if (message !is WebSocketMessage.Authenticate) continue

                    val userId = jwtService.getUserId(message.token)
                    if (userId == null || !deviceService.isOwnedBy(message.deviceId, userId)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid credentials or device"))
                        return@webSocket
                    }

                    authenticatedDeviceId = message.deviceId
                    webSocketService.registerConnection(message.deviceId, this)
                    sendSerialized(WebSocketMessage.Heartbeat(timestamp = System.currentTimeMillis()))
                    continue
                }

                val deviceId = authenticatedDeviceId

                when (message) {
                    is WebSocketMessage.Authenticate -> {
                        // Already authenticated on this socket; ignore re-auth attempts.
                    }
                    is WebSocketMessage.ScreenFrame -> {
                        if (webSocketService.isParticipant(message.sessionId, deviceId)) {
                            webSocketService.forwardToPeer(deviceId, message.sessionId, message)
                        }
                    }
                    is WebSocketMessage.InputEvent -> {
                        if (webSocketService.isParticipant(message.sessionId, deviceId)) {
                            webSocketService.forwardToPeer(deviceId, message.sessionId, message)
                        }
                    }
                    is WebSocketMessage.Disconnect -> {
                        if (webSocketService.isParticipant(message.sessionId, deviceId)) {
                            webSocketService.forwardToPeer(deviceId, message.sessionId, message)
                        }
                        webSocketService.endSession(message.sessionId)
                    }
                    is WebSocketMessage.Heartbeat -> {
                        sendSerialized(WebSocketMessage.Heartbeat(timestamp = System.currentTimeMillis()))
                    }
                    // ConnectionRequest / ConnectionAccepted / ConnectionRejected are server-pushed
                    // notifications only (triggered from ConnectionService via REST accept/reject);
                    // clients never originate them over the socket, so there's nothing to handle here.
                    else -> {}
                }
            }
        } finally {
            authenticatedDeviceId?.let { webSocketService.unregisterConnection(it) }
        }
    }
}
