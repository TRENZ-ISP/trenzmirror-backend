package com.trenz.mirror.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRequest(
    val id: String,
    val fromDeviceId: String,
    val fromDeviceName: String,
    val toDeviceId: String,
    val status: String,
    val createdAt: Long
)
