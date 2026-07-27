package com.trenz.mirror.routes.device

import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.device.DeviceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.deviceRoutes() {
    val deviceService = DeviceService()

    authenticate("auth-jwt") {
        route("/api/v1/devices") {
            post("/register") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<RegisterDeviceRequest>()
                val device = deviceService.registerDevice(userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.Success(device))
            }

            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val devices = deviceService.getDevices(userId)
                call.respond(HttpStatusCode.OK, ApiResponse.Success(devices))
            }

            post("/pair") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<PairDeviceRequest>()
                try {
                    val device = deviceService.pairDevice(userId, request)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(device))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("PAIRING_FAILED", e.message ?: "Unknown error"))
                }
            }

            post("/location") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<UpdateLocationRequest>()
                try {
                    deviceService.updateLocation(userId, request.deviceId, request.latitude, request.longitude)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(Unit))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("LOCATION_UPDATE_FAILED", e.message ?: "Unknown error"))
                }
            }

            get("/{deviceId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.parameters["deviceId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Device ID required")

                if (!deviceService.isOwnedBy(deviceId, userId)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ApiResponse.Error("NOT_YOUR_DEVICE", "This device does not belong to you"))
                }

                val device = deviceService.getDeviceById(deviceId, includePairingCode = true)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse.Error("DEVICE_NOT_FOUND", "Device not found"))

                call.respond(HttpStatusCode.OK, ApiResponse.Success(device))
            }

            post("/{deviceId}/regenerate-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.parameters["deviceId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Device ID required")

                try {
                    val device = deviceService.regeneratePairingCode(userId, deviceId)
                    call.respond(HttpStatusCode.OK, ApiResponse.Success(device))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("REGENERATE_FAILED", e.message ?: "Unknown error"))
                }
            }

            delete("/{deviceId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val deviceId = call.parameters["deviceId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Device ID required")

                deviceService.deleteDevice(userId, deviceId)
                call.respond(HttpStatusCode.OK, ApiResponse.Success(Unit))
            }
        }
    }
}
