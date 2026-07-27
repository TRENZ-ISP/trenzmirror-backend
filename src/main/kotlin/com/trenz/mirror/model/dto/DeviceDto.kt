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
    val locationUpdatedAt: Long? = null,
    // Only ever populated when a device is looking up ITS OWN record (getDevices, regenerate).
    // Never included on devices returned via pairing/paired-devices lookups - handing back
    // someone else's live pairing code there would let anyone who can already see a device
    // pair with it a second time without the owner's approval, defeating the whole point of
    // requiring an explicit accept step.
    val pairingCode: String? = null,
    val pairingCodeExpiresAt: Long? = null
)
