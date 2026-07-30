package com.trenz.mirror.routes.connection

import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.connection.ConnectionAuthorizationException
import com.trenz.mirror.service.connection.ConnectionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.connectionRoutes(connectionService: ConnectionService) {
    authenticate("auth-jwt") {
        route("/api/v1/connections") {
            post("/request") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<RequestConnectionRequest>()
                try {
                    val response = connectionService.requestConnection(userId, request)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(response))
                } catch (e: ConnectionAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("FORBIDDEN", e.message ?: "Not authorized"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("REQUEST_FAILED", e.message ?: "Unknown error"))
                }
            }

            post("/respond") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<RespondConnectionRequest>()
                try {
                    val response = connectionService.respondToConnection(userId, request)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(response))
                } catch (e: ConnectionAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("FORBIDDEN", e.message ?: "Not authorized"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("RESPONSE_FAILED", e.message ?: "Unknown error"))
                }
            }

            post("/{sessionId}/disconnect") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val sessionId = call.parameters["sessionId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("BAD_REQUEST", "Session ID required"))

                try {
                    connectionService.disconnectSession(userId, sessionId)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(Unit))
                } catch (e: ConnectionAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("FORBIDDEN", e.message ?: "Not authorized"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("DISCONNECT_FAILED", e.message ?: "Unknown error"))
                }
            }

            get("/pending") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.request.queryParameters["deviceId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("BAD_REQUEST", "Device ID required"))

                try {
                    val requests = connectionService.getPendingRequests(userId, deviceId)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(requests))
                } catch (e: ConnectionAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("FORBIDDEN", e.message ?: "Not authorized"))
                }
            }

            get("/recent") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.request.queryParameters["deviceId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("BAD_REQUEST", "Device ID required"))

                try {
                    val recent = connectionService.getRecentSessions(userId, deviceId)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(recent))
                } catch (e: ConnectionAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("FORBIDDEN", e.message ?: "Not authorized"))
                }
            }
        }
    }
}
