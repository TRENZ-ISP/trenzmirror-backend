package com.trenz.mirror.service.pairing

import com.trenz.mirror.database.tables.*
import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.device.DeviceService
import com.trenz.mirror.service.websocket.WebSocketService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class PairingAuthorizationException(message: String) : RuntimeException(message)

class PairingService(
    private val deviceService: DeviceService,
    private val webSocketService: WebSocketService
) {

    /**
     * [userId] must own [request.requesterDeviceId] - a device can only request pairing on its
     * own behalf, never impersonate another of the caller's devices.
     *
     * This only ever creates a PENDING request. Entering a valid code never immediately pairs
     * two devices - the owner of the target device must explicitly approve it first, so a code
     * leaking or being guessed doesn't hand over control on its own.
     */
    suspend fun requestPairing(userId: String, request: PairRequestByCodeRequest): PairRequestDto {
        if (!deviceService.isOwnedBy(request.requesterDeviceId, userId)) {
            throw PairingAuthorizationException("requesterDeviceId does not belong to the authenticated user")
        }

        val targetDevice = deviceService.findByPairingCode(request.pairingCode)
            ?: throw IllegalArgumentException("Invalid or expired pairing code")

        if (targetDevice.id == request.requesterDeviceId) {
            throw IllegalArgumentException("A device cannot pair with itself")
        }

        val requesterDevice = deviceService.getDeviceById(request.requesterDeviceId, includePairingCode = false)
            ?: throw IllegalArgumentException("Requesting device not found")

        val dto = transaction {
            val requestId = UUID.randomUUID().toString()
            PairRequestsTable.insert {
                it[PairRequestsTable.id] = UUID.fromString(requestId)
                it[requesterDeviceId] = UUID.fromString(request.requesterDeviceId)
                it[targetDeviceId] = UUID.fromString(targetDevice.id)
                it[status] = "PENDING"
                it[createdAt] = LocalDateTime.now()
            }

            PairRequestDto(
                id = requestId,
                requesterDeviceId = request.requesterDeviceId,
                requesterDeviceName = requesterDevice.name,
                targetDeviceId = targetDevice.id,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
        }

        // Realtime push so the owner sees it immediately if they're online; falls back to
        // GET /pair/pending on next load either way.
        webSocketService.sendToDevice(
            targetDevice.id,
            WebSocketMessage.PairRequest(
                requestId = dto.id,
                requesterDeviceId = dto.requesterDeviceId,
                requesterDeviceName = dto.requesterDeviceName
            )
        )

        return dto
    }

    /**
     * [userId] must own the request's targetDeviceId - only the device being asked to pair can
     * approve or decline it.
     */
    suspend fun respondToPairing(userId: String, request: RespondPairRequestRequest): PairRequestDto {
        val row = transaction {
            PairRequestsTable.select { PairRequestsTable.id eq UUID.fromString(request.requestId) }.singleOrNull()
        } ?: throw IllegalArgumentException("Pair request not found")

        val targetDeviceId = row[PairRequestsTable.targetDeviceId].toString()
        if (!deviceService.isOwnedBy(targetDeviceId, userId)) {
            throw PairingAuthorizationException("This pair request is not addressed to a device you own")
        }

        val requesterDeviceId = row[PairRequestsTable.requesterDeviceId].toString()
        val newStatus = if (request.accept) "ACCEPTED" else "REJECTED"

        transaction {
            PairRequestsTable.update({ PairRequestsTable.id eq UUID.fromString(request.requestId) }) {
                it[status] = newStatus
                it[respondedAt] = LocalDateTime.now()
            }

            if (request.accept) {
                val now = LocalDateTime.now()
                // Two rows so a lookup from either device's side is a plain equality query.
                PairedDevicesTable.insert {
                    it[ownerDeviceId] = UUID.fromString(targetDeviceId)
                    it[pairedDeviceId] = UUID.fromString(requesterDeviceId)
                    it[status] = "ACTIVE"
                    it[createdAt] = now
                }
                PairedDevicesTable.insert {
                    it[ownerDeviceId] = UUID.fromString(requesterDeviceId)
                    it[pairedDeviceId] = UUID.fromString(targetDeviceId)
                    it[status] = "ACTIVE"
                    it[createdAt] = now
                }
                DevicesTable.update({ DevicesTable.id eq UUID.fromString(targetDeviceId) }) {
                    it[isPaired] = true
                    it[pairedAt] = now
                }
                DevicesTable.update({ DevicesTable.id eq UUID.fromString(requesterDeviceId) }) {
                    it[isPaired] = true
                    it[pairedAt] = now
                }
            }
        }

        val targetDevice = deviceService.getDeviceById(targetDeviceId, includePairingCode = false)

        if (request.accept) {
            webSocketService.sendToDevice(
                requesterDeviceId,
                WebSocketMessage.PairAccepted(
                    requestId = request.requestId,
                    pairedDeviceId = targetDeviceId,
                    pairedDeviceName = targetDevice?.name ?: ""
                )
            )
        } else {
            webSocketService.sendToDevice(
                requesterDeviceId,
                WebSocketMessage.PairRejected(requestId = request.requestId)
            )
        }

        return PairRequestDto(
            id = request.requestId,
            requesterDeviceId = requesterDeviceId,
            requesterDeviceName = "",
            targetDeviceId = targetDeviceId,
            status = newStatus,
            createdAt = row[PairRequestsTable.createdAt].toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        )
    }

    /** [userId] must own [deviceId]. Returns pending requests where this device is the one being asked. */
    fun getPendingPairRequests(userId: String, deviceId: String): List<PairRequestDto> {
        if (!deviceService.isOwnedBy(deviceId, userId)) {
            throw PairingAuthorizationException("deviceId does not belong to the authenticated user")
        }
        return transaction {
            PairRequestsTable.select {
                (PairRequestsTable.targetDeviceId eq UUID.fromString(deviceId)) and
                (PairRequestsTable.status eq "PENDING")
            }.map { row ->
                val requesterId = row[PairRequestsTable.requesterDeviceId].toString()
                val requesterDevice = deviceService.getDeviceById(requesterId, includePairingCode = false)
                PairRequestDto(
                    id = row[PairRequestsTable.id].toString(),
                    requesterDeviceId = requesterId,
                    requesterDeviceName = requesterDevice?.name ?: "Unknown",
                    targetDeviceId = deviceId,
                    status = row[PairRequestsTable.status],
                    createdAt = row[PairRequestsTable.createdAt].toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                )
            }
        }
    }

    /** [userId] must own [deviceId]. Returns every device permanently paired with it (pairing codes never included). */
    fun getPairedDevices(userId: String, deviceId: String): List<DeviceDto> {
        if (!deviceService.isOwnedBy(deviceId, userId)) {
            throw PairingAuthorizationException("deviceId does not belong to the authenticated user")
        }
        return transaction {
            PairedDevicesTable.select {
                (PairedDevicesTable.ownerDeviceId eq UUID.fromString(deviceId)) and
                (PairedDevicesTable.status eq "ACTIVE")
            }.mapNotNull { row ->
                deviceService.getDeviceById(row[PairedDevicesTable.pairedDeviceId].toString(), includePairingCode = false)
            }
        }
    }
}
