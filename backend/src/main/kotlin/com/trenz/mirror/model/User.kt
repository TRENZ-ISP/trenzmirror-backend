package com.trenz.mirror.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String,
    val createdAt: Long
)
