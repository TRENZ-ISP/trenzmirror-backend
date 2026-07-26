package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateLocationRequest(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double
)
