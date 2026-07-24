package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RespondConnectionRequest(
    val requestId: String,
    val accept: Boolean
)
