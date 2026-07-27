package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RespondPairRequestRequest(
    val requestId: String,
    val accept: Boolean
)
