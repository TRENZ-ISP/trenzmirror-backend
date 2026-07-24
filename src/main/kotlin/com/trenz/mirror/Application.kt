package com.trenz.mirror

import com.trenz.mirror.config.configureDatabase
import com.trenz.mirror.config.configureSecurity
import com.trenz.mirror.config.configureSerialization
import com.trenz.mirror.config.configureWebSockets
import com.trenz.mirror.routes.auth.authRoutes
import com.trenz.mirror.routes.connection.connectionRoutes
import com.trenz.mirror.routes.device.deviceRoutes
import com.trenz.mirror.routes.websocket.webSocketRoutes
import com.trenz.mirror.service.auth.JwtService
import com.trenz.mirror.service.device.DeviceService
import com.trenz.mirror.service.connection.ConnectionService
import com.trenz.mirror.service.websocket.WebSocketService
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Previously this nested call.respond(...) inside call.respond(...), which doesn't
            // compile (respond() returns Unit, not a value you can pass to another respond()).
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Internal server error")))
        }
    }

    configureDatabase()
    configureSerialization()
    configureSecurity()
    configureWebSockets()

    // Shared, application-scoped instances. WebSocketService in particular holds in-memory
    // connection/session state and must be a single instance across all routes - the previous
    // version instantiated a separate WebSocketService() inside webSocketRoutes() with no way
    // for ConnectionService (handling REST accept/reject) to push notifications through it.
    val deviceService = DeviceService()
    val jwtService = JwtService(environment)
    val webSocketService = WebSocketService(deviceService)
    val connectionService = ConnectionService(deviceService, webSocketService)

    routing {
        get("/") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
        authRoutes()
        deviceRoutes()
        connectionRoutes(connectionService)
        webSocketRoutes(webSocketService, jwtService, deviceService)
    }
}
