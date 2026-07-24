package com.trenz.mirror.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object ConnectionRequestsTable : UUIDTable("connection_requests") {
    val fromDeviceId = reference("from_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val toDeviceId = reference("to_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 50).default("PENDING")
    val createdAt = datetime("created_at")
    val respondedAt = datetime("responded_at").nullable()
}
