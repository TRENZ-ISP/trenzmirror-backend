package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val name: String,
    val deviceModel: String,
    val osVersion: String
)
