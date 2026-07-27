package com.trenz.mirror.routes.pairing

import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.pairing.PairingAuthorizationException
import com.trenz.mirror.service.pairing.PairingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pairingRoutes(pairingService: PairingService) {
    authenticate("auth-jwt") {
        route("/api/v1/pair") {
            post("/request") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<PairRequestByCodeRequest>()
                try {
                    val response = pairingService.requestPairing(userId, request)
                    call.respond(HttpStatusCode.Created, ApiResponse.Success(response))
                } catch (e: PairingAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("NOT_AUTHORIZED", e.message ?: "Not authorized"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("PAIR_REQUEST_FAILED", e.message ?: "Invalid or expired pairing code"))
                }
            }

            post("/respond") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<RespondPairRequestRequest>()
                try {
                    val response = pairingService.respondToPairing(userId, request)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(response))
                } catch (e: PairingAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("NOT_AUTHORIZED", e.message ?: "Not authorized"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("PAIR_RESPOND_FAILED", e.message ?: "Unknown error"))
                }
            }

            get("/pending") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.request.queryParameters["deviceId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "deviceId query parameter required")

                try {
                    val pending = pairingService.getPendingPairRequests(userId, deviceId)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(pending))
                } catch (e: PairingAuthorizationException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("NOT_AUTHORIZED", e.message ?: "Not authorized"))
                }
            }
        }

        get("/api/v1/paired-devices") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val deviceId = call.request.queryParameters["deviceId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "deviceId query parameter required")

            try {
                val paired = pairingService.getPairedDevices(userId, deviceId)
                call.respond(HttpStatusCode.OK, ApiResponse.Success(paired))
            } catch (e: PairingAuthorizationException) {
                call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("NOT_AUTHORIZED", e.message ?: "Not authorized"))
            }
        }
    }
}
