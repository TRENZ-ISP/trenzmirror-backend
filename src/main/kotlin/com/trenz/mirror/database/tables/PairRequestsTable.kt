package com.trenz.mirror.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object PairRequestsTable : UUIDTable("pair_requests") {
    val requesterDeviceId = reference("requester_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val targetDeviceId = reference("target_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 50).default("PENDING")
    val createdAt = datetime("created_at")
    val respondedAt = datetime("responded_at").nullable()
}
