package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairRequestDto(
    val id: String,
    val requesterDeviceId: String,
    val requesterDeviceName: String,
    val targetDeviceId: String,
    val status: String,
    val createdAt: Long
)
