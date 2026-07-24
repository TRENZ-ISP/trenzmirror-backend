package com.trenz.mirror.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val userId: String,
    val name: String,
    val deviceModel: String,
    val osVersion: String,
    val isOnline: Boolean = false,
    val isPaired: Boolean = false,
    val pairedAt: Long? = null,
    val lastSeenAt: Long? = null
)
