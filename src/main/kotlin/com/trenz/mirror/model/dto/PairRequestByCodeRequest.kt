package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairRequestByCodeRequest(
    val requesterDeviceId: String,
    val pairingCode: String
)
