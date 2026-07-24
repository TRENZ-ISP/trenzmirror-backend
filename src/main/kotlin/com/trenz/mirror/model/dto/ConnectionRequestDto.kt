package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRequestDto(
    val id: String,
    val fromDeviceId: String,
    val fromDeviceName: String,
    val toDeviceId: String,
    val status: String,
    val createdAt: Long,
    // Populated only when status == ACCEPTED - lets the accepting (controlled) device know
    // which session to tag its screen frames with, without a separate round trip.
    val sessionId: String? = null
)
