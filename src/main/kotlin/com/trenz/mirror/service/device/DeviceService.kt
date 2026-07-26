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
            DevicesTable.insert {
                it[DevicesTable.id] = UUID.fromString(deviceId)
                it[DevicesTable.userId] = UUID.fromString(userId)
                it[name] = request.name
                it[deviceModel] = request.deviceModel
                it[osVersion] = request.osVersion
                it[isOnline] = true
                it[isPaired] = false
                it[createdAt] = LocalDateTime.now()
                it[lastSeenAt] = LocalDateTime.now()
            }

            DeviceDto(
                id = deviceId,
                userId = userId,
                name = request.name,
                deviceModel = request.deviceModel,
                osVersion = request.osVersion,
                isOnline = true,
                isPaired = false
            )
        }
    }

    fun getDevices(userId: String): List<DeviceDto> {
        return transaction {
            DevicesTable.select { DevicesTable.userId eq UUID.fromString(userId) }
                .map { row ->
                    DeviceDto(
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
                        locationUpdatedAt = row[DevicesTable.locationUpdatedAt]?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000)
                    )
                }
        }
    }

    /** [userId] must own [deviceId]; a device can only report its own location, never another's. */
    fun updateLocation(userId: String, deviceId: String, latitude: Double, longitude: Double) {
        if (!isOwnedBy(deviceId, userId)) {
            throw IllegalArgumentException("deviceId does not belong to the authenticated user")
        }
        transaction {
            DevicesTable.update({ DevicesTable.id eq UUID.fromString(deviceId) }) {
                it[DevicesTable.latitude] = latitude
                it[DevicesTable.longitude] = longitude
                it[locationUpdatedAt] = LocalDateTime.now()
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
