package com.trenz.mirror.service.auth

import com.trenz.mirror.database.tables.*
import com.trenz.mirror.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.UUID

class AuthService(private val jwtService: JwtService) {

    fun register(request: RegisterRequest): RegisterResponse {
        return transaction {
            val existingUser = UsersTable.select { UsersTable.email eq request.email }.singleOrNull()
            if (existingUser != null) {
                throw IllegalArgumentException("Email already registered")
            }

            val userId = UUID.randomUUID().toString()
            val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt(12))

            UsersTable.insert {
                it[id] = UUID.fromString(userId)
                it[email] = request.email
                it[username] = request.username
                it[UsersTable.passwordHash] = passwordHash
                it[createdAt] = LocalDateTime.now()
            }

            RegisterResponse(
                id = userId,
                email = request.email,
                username = request.username,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    fun login(request: LoginRequest): LoginResponse {
        return transaction {
            val identifier = request.identifier.trim()
            // Accepts either the account's username or email in the same field, matched
            // case-insensitively - firstOrNull() rather than singleOrNull() since username
            // uniqueness isn't enforced at the database level, only email is guaranteed unique.
            val userRow = UsersTable.select {
                (UsersTable.email.lowerCase() eq identifier.lowercase()) or
                (UsersTable.username.lowerCase() eq identifier.lowercase())
            }.firstOrNull() ?: throw IllegalArgumentException("Invalid credentials")

            val passwordHash = userRow[UsersTable.passwordHash]
            if (!BCrypt.checkpw(request.password, passwordHash)) {
                throw IllegalArgumentException("Invalid credentials")
            }

            val userId = userRow[UsersTable.id].toString()
            val userEmail = userRow[UsersTable.email]
            val accessToken = jwtService.generateAccessToken(userId, userEmail)
            val refreshToken = jwtService.generateRefreshToken(userId)
            val expiresAt = System.currentTimeMillis() + 15 * 60 * 1000

            RefreshTokensTable.insert {
                it[RefreshTokensTable.userId] = UUID.fromString(userId)
                it[token] = refreshToken
                it[RefreshTokensTable.expiresAt] = LocalDateTime.now().plusDays(7)
                it[createdAt] = LocalDateTime.now()
            }

            LoginResponse(
                user = UserDto(
                    id = userId,
                    email = userRow[UsersTable.email],
                    username = userRow[UsersTable.username],
                    createdAt = userRow[UsersTable.createdAt].toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                ),
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt
            )
        }
    }

    fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        return transaction {
            val tokenRow = RefreshTokensTable.select { RefreshTokensTable.token eq request.refreshToken }.singleOrNull()
                ?: throw IllegalArgumentException("Invalid refresh token")

            if (tokenRow[RefreshTokensTable.expiresAt].isBefore(LocalDateTime.now())) {
                throw IllegalArgumentException("Refresh token expired")
            }

            val userId = tokenRow[RefreshTokensTable.userId].toString()
            val userRow = UsersTable.select { UsersTable.id eq UUID.fromString(userId) }.singleOrNull()
                ?: throw IllegalArgumentException("User not found")

            val newAccessToken = jwtService.generateAccessToken(userId, userRow[UsersTable.email])
            val newRefreshToken = jwtService.generateRefreshToken(userId)
            val expiresAt = System.currentTimeMillis() + 15 * 60 * 1000

            RefreshTokensTable.update({ RefreshTokensTable.id eq tokenRow[RefreshTokensTable.id] }) {
                it[token] = newRefreshToken
                it[RefreshTokensTable.expiresAt] = LocalDateTime.now().plusDays(7)
            }

            RefreshTokenResponse(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAt = expiresAt
            )
        }
    }
}
