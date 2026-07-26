package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val id: String,
    val userId: String,
    val name: String,
    val deviceModel: String,
    val osVersion: String,
    val isOnline: Boolean = false,
    val isPaired: Boolean = false,
    val pairedAt: Long? = null,
    val lastSeenAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationUpdatedAt: Long? = null
)
