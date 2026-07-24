package com.trenz.mirror.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val controllerDeviceId: String,
    val controlledDeviceId: String,
    val status: String,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val createdAt: Long
)
