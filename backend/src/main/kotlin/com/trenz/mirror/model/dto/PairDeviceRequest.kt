package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairDeviceRequest(
    val deviceId: String
)
