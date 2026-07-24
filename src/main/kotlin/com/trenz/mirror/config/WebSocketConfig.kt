package com.trenz.mirror.config

import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json


fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = java.time.Duration.ofSeconds(30)
        timeout = java.time.Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        // Required for sendSerialized()/receiveDeserialized() (used throughout WebSocketService
        // and WebSocketRoutes) to work at all - without a converter they throw at runtime.
        contentConverter = KotlinxWebsocketSerializationConverter(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}
