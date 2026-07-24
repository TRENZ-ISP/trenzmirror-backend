package com.trenz.mirror.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object SessionsTable : UUIDTable("sessions") {
    val controllerDeviceId = reference("controller_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val controlledDeviceId = reference("controlled_device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 50).default("PENDING")
    val startedAt = datetime("started_at").nullable()
    val endedAt = datetime("ended_at").nullable()
    val createdAt = datetime("created_at")
}
