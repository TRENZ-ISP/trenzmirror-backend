package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RequestConnectionRequest(
    // The device (belonging to the authenticated user) that is initiating the request.
    val fromDeviceId: String,
    val targetDeviceId: String
)
