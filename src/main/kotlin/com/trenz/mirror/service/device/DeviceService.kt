package com.trenz.mirror.service.device

import com.trenz.mirror.database.tables.*
import com.trenz.mirror.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class DeviceService {

    fun registerDevice(userId: String, request: RegisterDeviceRequest): DeviceDto {
        return transaction {
            val deviceId = UUID.randomUUID().toString()
            val code = generateUniquePairingCode()
            val now = LocalDateTime.now()
            DevicesTable.insert {
                it[DevicesTable.id] = UUID.fromString(deviceId)
                it[DevicesTable.userId] = UUID.fromString(userId)
                it[name] = request.name
                it[deviceModel] = request.deviceModel
                it[osVersion] = request.osVersion
                it[isOnline] = true
                it[isPaired] = false
                it[createdAt] = now
                it[lastSeenAt] = now
                it[pairingCode] = code
                it[pairingCodeCreatedAt] = now
                it[pairingCodeExpiresAt] = now.plusDays(30)
            }

            DeviceDto(
                id = deviceId,
                userId = userId,
                name = request.name,
                deviceModel = request.deviceModel,
                osVersion = request.osVersion,
                isOnline = true,
                isPaired = false,
                pairingCode = code,
                pairingCodeExpiresAt = now.plusDays(30).toEpochSecond(java.time.ZoneOffset.UTC) * 1000
            )
        }
    }

    /** Generates a pairing code, retrying on the astronomically unlikely event of a collision. */
    private fun generateUniquePairingCode(): String {
        repeat(10) {
            val candidate = com.trenz.mirror.util.PairingCodeGenerator.generate()
            val exists = DevicesTable.select { DevicesTable.pairingCode eq candidate }.count() > 0
            if (!exists) return candidate
        }
        throw IllegalStateException("Could not generate a unique pairing code")
    }

    /** Regenerates [deviceId]'s pairing code - invalidates the old one immediately. [userId] must own the device. */
    fun regeneratePairingCode(userId: String, deviceId: String): DeviceDto {
        if (!isOwnedBy(deviceId, userId)) {
            throw IllegalArgumentException("deviceId does not belong to the authenticated user")
        }
        return transaction {
            val code = generateUniquePairingCode()
            val now = LocalDateTime.now()
            DevicesTable.update({ DevicesTable.id eq UUID.fromString(deviceId) }) {
                it[pairingCode] = code
                it[pairingCodeCreatedAt] = now
                it[pairingCodeExpiresAt] = now.plusDays(30)
            }
            getDeviceById(deviceId, includePairingCode = true)
                ?: throw IllegalStateException("Device disappeared mid-update")
        }
    }

    /** Looks up a device by its current, non-expired pairing code. Returns null if not found or expired. */
    fun findByPairingCode(code: String): DeviceDto? {
        return transaction {
            val row = DevicesTable.select { DevicesTable.pairingCode eq code }.singleOrNull() ?: return@transaction null
            val expiresAt = row[DevicesTable.pairingCodeExpiresAt] ?: return@transaction null
            if (expiresAt.isBefore(LocalDateTime.now())) return@transaction null
            rowToDto(row, includePairingCode = false)
        }
    }

    fun getDeviceById(deviceId: String, includePairingCode: Boolean): DeviceDto? {
        return transaction {
            val row = DevicesTable.select { DevicesTable.id eq UUID.fromString(deviceId) }.singleOrNull()
                ?: return@transaction null
            rowToDto(row, includePairingCode)
        }
    }

    private fun rowToDto(row: ResultRow, includePairingCode: Boolean): DeviceDto = DeviceDto(
        id = row[DevicesTable.id].toString(),
        userId = row[DevicesTable.userId].toString(),
        name = row[DevicesTable.name],
        deviceModel = row[DevicesTable.deviceModel],
        osVersion = row[DevicesTable.osVersion],
        isOnline = row[DevicesTable.isOnline],
        isPaired = row[DevicesTable.isPaired],
        pairedAt = row[DevicesTable.pairedAt]?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000),
        lastSeenAt = row[DevicesTable.lastSeenAt]?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000),
        latitude = row[DevicesTable.latitude],
        longitude = row[DevicesTable.longitude],
        locationUpdatedAt = row[DevicesTable.locationUpdatedAt]?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000),
        pairingCode = if (includePairingCode) row[DevicesTable.pairingCode] else null,
        pairingCodeExpiresAt = if (includePairingCode)
            row[DevicesTable.pairingCodeExpiresAt]?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000)
        else null,
        batteryLevel = row[DevicesTable.batteryLevel],
        networkQuality = row[DevicesTable.networkQuality]
    )

    fun getDevices(userId: String): List<DeviceDto> {
        return transaction {
            DevicesTable.select { DevicesTable.userId eq UUID.fromString(userId) }
                .map { row -> rowToDto(row, includePairingCode = true) }
        }
    }

    /** [userId] must own [deviceId]; a device can only report its own location, never another's. */
    fun updateLocation(
        userId: String,
        deviceId: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int? = null,
        networkQuality: String? = null
    ) {
        if (!isOwnedBy(deviceId, userId)) {
            throw IllegalArgumentException("deviceId does not belong to the authenticated user")
        }
        transaction {
            DevicesTable.update({ DevicesTable.id eq UUID.fromString(deviceId) }) {
                it[DevicesTable.latitude] = latitude
                it[DevicesTable.longitude] = longitude
                it[locationUpdatedAt] = LocalDateTime.now()
                if (batteryLevel != null) it[DevicesTable.batteryLevel] = batteryLevel
                if (networkQuality != null) it[DevicesTable.networkQuality] = networkQuality
            }
        }
    }

    fun pairDevice(userId: String, request: PairDeviceRequest): DeviceDto {
        return transaction {
            val device = DevicesTable.select {
                (DevicesTable.id eq UUID.fromString(request.deviceId)) and
                (DevicesTable.userId eq UUID.fromString(userId))
            }.singleOrNull() ?: throw IllegalArgumentException("Device not found")

            DevicesTable.update({ DevicesTable.id eq UUID.fromString(request.deviceId) }) {
                it[isPaired] = true
                it[pairedAt] = LocalDateTime.now()
            }

            DeviceDto(
                id = request.deviceId,
                userId = userId,
                name = device[DevicesTable.name],
                deviceModel = device[DevicesTable.deviceModel],
                osVersion = device[DevicesTable.osVersion],
                isOnline = device[DevicesTable.isOnline],
                isPaired = true,
                pairedAt = System.currentTimeMillis()
            )
        }
    }

    fun deleteDevice(userId: String, deviceId: String) {
        transaction {
            DevicesTable.deleteWhere {
                (DevicesTable.id eq UUID.fromString(deviceId)) and
                (DevicesTable.userId eq UUID.fromString(userId))
            }
        }
    }

    fun updateDeviceStatus(deviceId: String, isOnline: Boolean) {
        transaction {
            DevicesTable.update({ DevicesTable.id eq UUID.fromString(deviceId) }) {
                it[DevicesTable.isOnline] = isOnline
                it[lastSeenAt] = LocalDateTime.now()
            }
        }
    }

    /** Returns true only if [deviceId] exists and is owned by [userId]. Never throws. */
    fun isOwnedBy(deviceId: String, userId: String): Boolean {
        return try {
            transaction {
                DevicesTable.select {
                    (DevicesTable.id eq UUID.fromString(deviceId)) and
                    (DevicesTable.userId eq UUID.fromString(userId))
                }.count() > 0
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
