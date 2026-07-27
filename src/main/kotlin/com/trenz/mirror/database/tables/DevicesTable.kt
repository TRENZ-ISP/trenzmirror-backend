package com.trenz.mirror.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object DevicesTable : UUIDTable("devices") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val deviceModel = varchar("device_model", 255)
    val osVersion = varchar("os_version", 50)
    val isOnline = bool("is_online").default(false)
    val isPaired = bool("is_paired").default(false)
    val pairedAt = datetime("paired_at").nullable()
    val lastSeenAt = datetime("last_seen_at").nullable()
    val createdAt = datetime("created_at")
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val locationUpdatedAt = datetime("location_updated_at").nullable()
    val pairingCode = varchar("pairing_code", 16).uniqueIndex().nullable()
    val pairingCodeCreatedAt = datetime("pairing_code_created_at").nullable()
    val pairingCodeExpiresAt = datetime("pairing_code_expires_at").nullable()
}
