package com.trenz.mirror.routes.auth

import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.auth.AuthService
import com.trenz.mirror.service.auth.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    val jwtService = JwtService(application.environment)
    val authService = AuthService(jwtService)

    route("/api/v1/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            try {
                val response = authService.register(request)
                call.respond(HttpStatusCode.Created, ApiResponse.Success(response))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.Error("REGISTRATION_FAILED", e.message ?: "Unknown error"))
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            try {
                val response = authService.login(request)
                call.respond(HttpStatusCode.OK, ApiResponse.Success(response))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse.Error("INVALID_CREDENTIALS", e.message ?: "Login failed"))
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            try {
                val response = authService.refreshToken(request)
                call.respond(HttpStatusCode.OK, ApiResponse.Success(response))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse.Error("INVALID_TOKEN", e.message ?: "Token refresh failed"))
            }
        }
    }
}
