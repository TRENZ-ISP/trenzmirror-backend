package com.trenz.mirror.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// IMPORTANT: none of these subclasses declare their own `type` property. kotlinx.serialization's
// default polymorphic JSON encoding already injects a discriminator key called "type" (Json's
// default classDiscriminator) containing the @SerialName below. The original version of this
// file ALSO declared `val type: String = "..."` on every subclass, which collides with that
// auto-injected discriminator key and throws
// `IllegalArgumentException: Class discriminator collides with a property name 'type'` the very
// first time ANY message (including the initial Heartbeat exchanged right after connecting) is
// serialized or deserialized. That bug alone would have broken the entire WebSocket layer on
// both ends regardless of anything else being correct.
@Serializable
sealed class WebSocketMessage {
    @Serializable
    @SerialName("connection_request")
    data class ConnectionRequest(
        val requestId: String,
        val fromDeviceId: String,
        val fromDeviceName: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("connection_accepted")
    data class ConnectionAccepted(
        val requestId: String,
        val sessionId: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("connection_rejected")
    data class ConnectionRejected(
        val requestId: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("pair_request")
    data class PairRequest(
        val requestId: String,
        val requesterDeviceId: String,
        val requesterDeviceName: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("pair_accepted")
    data class PairAccepted(
        val requestId: String,
        val pairedDeviceId: String,
        val pairedDeviceName: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("pair_rejected")
    data class PairRejected(
        val requestId: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("screen_frame")
    data class ScreenFrame(
        val sessionId: String,
        val frameData: String,
        val width: Int,
        val height: Int,
        val timestamp: Long
    ) : WebSocketMessage()

    @Serializable
    @SerialName("input_event")
    data class InputEvent(
        val sessionId: String,
        val eventType: String,
        val x: Float = 0f,
        val y: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f,
        val deltaX: Float = 0f,
        val deltaY: Float = 0f,
        val keyCode: Int = 0,
        val text: String = "",
        val pointerId: Int = 0,
        val action: Int = 0,
        val duration: Long = 0
    ) : WebSocketMessage()

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        val timestamp: Long
    ) : WebSocketMessage()

    @Serializable
    @SerialName("disconnect")
    data class Disconnect(
        val sessionId: String,
        val reason: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("authenticate")
    data class Authenticate(
        val token: String,
        val deviceId: String
    ) : WebSocketMessage()
}
