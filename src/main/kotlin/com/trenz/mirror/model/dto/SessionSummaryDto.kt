package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionSummaryDto(
    val sessionId: String,
    val otherDeviceId: String,
    val otherDeviceName: String,
    // "Viewed" if this device was the controller, "Was Viewed" if it was the controlled device -
    // lets the UI show which direction the connection went without a separate lookup.
    val role: String,
    val status: String,
    val startedAt: Long?,
    val endedAt: Long?
)
