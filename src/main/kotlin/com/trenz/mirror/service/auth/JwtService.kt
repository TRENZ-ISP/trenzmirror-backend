package com.trenz.mirror.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.server.application.*
import java.util.Date

class JwtService(environment: ApplicationEnvironment) {
    private val secret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "default-secret-change-me"
    private val issuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "trenz-mirror"
    private val audience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "trenz-mirror-android"
    val realm = environment.config.propertyOrNull("jwt.realm")?.getString() ?: "TrenzMirror"
    private val accessTokenExpiry = 15 * 60 * 1000L
    private val refreshTokenExpiry = 7 * 24 * 60 * 60 * 1000L

    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(secret))
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun generateAccessToken(userId: String, email: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenExpiry))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(userId: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenExpiry))
            .sign(Algorithm.HMAC256(secret))
    }

    fun verifyToken(token: String): Boolean {
        return try {
            verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies [token] and returns the userId claim, or null if the token is missing,
     * expired, malformed, or not an access token. Used by the WebSocket layer to
     * authenticate a connection before trusting any client-claimed deviceId.
     */
    fun getUserId(token: String): String? {
        return try {
            val decoded = verifier.verify(token)
            if (decoded.getClaim("type").asString() != "access") return null
            decoded.getClaim("userId")?.asString()
        } catch (e: Exception) {
            null
        }
    }
}
