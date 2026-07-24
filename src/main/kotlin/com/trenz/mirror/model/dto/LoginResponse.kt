package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)
