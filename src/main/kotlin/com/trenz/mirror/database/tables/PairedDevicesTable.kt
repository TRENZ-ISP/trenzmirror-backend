package com.trenz.mirror.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * A confirmed, permanent pairing between two devices - created only after the owner of the
 * target device explicitly approves a PairRequestsTable entry. Two rows are written per accepted
 * pairing (owner->paired and paired->owner) so "give me everyone paired with device X" is always
 * a plain ownerDeviceId lookup from either side, rather than an OR query across two columns.
 */
object PairedDevicesTable : UUIDTable("paired_devices") {
    val ownerDeviceId = reference("owner_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val pairedDeviceId = reference("paired_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 50).default("ACTIVE")
    val createdAt = datetime("created_at")
}
