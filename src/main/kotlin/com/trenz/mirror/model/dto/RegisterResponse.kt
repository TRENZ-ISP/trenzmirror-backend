package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val id: String,
    val email: String,
    val username: String,
    val createdAt: Long
)
