package com.trenz.mirror.service.connection

import com.trenz.mirror.database.tables.*
import com.trenz.mirror.model.dto.*
import com.trenz.mirror.service.device.DeviceService
import com.trenz.mirror.service.websocket.WebSocketService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class ConnectionAuthorizationException(message: String) : RuntimeException(message)

class ConnectionService(
    private val deviceService: DeviceService,
    private val webSocketService: WebSocketService
) {

    /** [userId] must own [request.fromDeviceId]; the previous version silently trusted the caller. */
    suspend fun requestConnection(userId: String, request: RequestConnectionRequest): ConnectionRequestDto {
        if (!deviceService.isOwnedBy(request.fromDeviceId, userId)) {
            throw ConnectionAuthorizationException("fromDeviceId does not belong to the authenticated user")
        }

        val alreadyPaired = transaction {
            PairedDevicesTable.select {
                (PairedDevicesTable.ownerDeviceId eq UUID.fromString(request.fromDeviceId)) and
                (PairedDevicesTable.pairedDeviceId eq UUID.fromString(request.targetDeviceId)) and
                (PairedDevicesTable.status eq "ACTIVE")
            }.count() > 0
        }

        val dto = transaction {
            val requestId = UUID.randomUUID().toString()
            val fromDevice = DevicesTable.select { DevicesTable.id eq UUID.fromString(request.fromDeviceId) }
                .singleOrNull() ?: throw IllegalArgumentException("Device not found")

            // Trusted (already permanently paired) devices skip the manual accept/reject step
            // entirely - the request is recorded as immediately ACCEPTED rather than PENDING, so
            // it never shows up for the other side to approve. The required screen-sharing
            // notification still appears either way - this only removes the repeated
            // "do you want to connect" prompt between two devices that already trust each other.
            val status = if (alreadyPaired) "ACCEPTED" else "PENDING"

            ConnectionRequestsTable.insert {
                it[ConnectionRequestsTable.id] = UUID.fromString(requestId)
                it[ConnectionRequestsTable.fromDeviceId] = UUID.fromString(request.fromDeviceId)
                it[ConnectionRequestsTable.toDeviceId] = UUID.fromString(request.targetDeviceId)
                it[ConnectionRequestsTable.status] = status
                it[createdAt] = LocalDateTime.now()
                if (alreadyPaired) it[respondedAt] = LocalDateTime.now()
            }

            var sessionId: String? = null
            if (alreadyPaired) {
                val newSessionId = UUID.randomUUID().toString()
                sessionId = newSessionId
                SessionsTable.insert {
                    it[SessionsTable.id] = UUID.fromString(newSessionId)
                    it[controllerDeviceId] = UUID.fromString(request.fromDeviceId)
                    it[controlledDeviceId] = UUID.fromString(request.targetDeviceId)
                    it[SessionsTable.status] = "ACTIVE"
                    it[startedAt] = LocalDateTime.now()
                    it[createdAt] = LocalDateTime.now()
                }
            }

            ConnectionRequestDto(
                id = requestId,
                fromDeviceId = request.fromDeviceId,
                fromDeviceName = fromDevice[DevicesTable.name],
                toDeviceId = request.targetDeviceId,
                status = status,
                createdAt = System.currentTimeMillis(),
                sessionId = sessionId
            )
        }

        if (alreadyPaired && dto.sessionId != null) {
            // Skip straight to "session started" for both sides - no pending-request push needed.
            webSocketService.registerSession(dto.sessionId, request.fromDeviceId, request.targetDeviceId)
            webSocketService.sendToDevice(
                request.targetDeviceId,
                WebSocketMessage.ConnectionAccepted(requestId = dto.id, sessionId = dto.sessionId)
            )
        } else {
            // Realtime push so the target device doesn't have to poll /pending. Best-effort:
            // if the target isn't currently connected it'll still see the request next time it
            // calls GET /connections/pending.
            webSocketService.sendToDevice(
                request.targetDeviceId,
                WebSocketMessage.ConnectionRequest(
                    requestId = dto.id,
                    fromDeviceId = dto.fromDeviceId,
                    fromDeviceName = dto.fromDeviceName
                )
            )
        }

        return dto
    }

    /**
     * [userId] must own the request's toDeviceId (i.e. only the device being asked can accept
     * or reject it). The previous version accepted a userId parameter and never checked it.
     */
    suspend fun respondToConnection(userId: String, request: RespondConnectionRequest): ConnectionRequestDto {
        val connectionRequest = transaction {
            ConnectionRequestsTable.select {
                ConnectionRequestsTable.id eq UUID.fromString(request.requestId)
            }.singleOrNull()
        } ?: throw IllegalArgumentException("Connection request not found")

        val toDeviceId = connectionRequest[ConnectionRequestsTable.toDeviceId].toString()
        if (!deviceService.isOwnedBy(toDeviceId, userId)) {
            throw ConnectionAuthorizationException("This connection request is not addressed to a device you own")
        }

        val fromDeviceId = connectionRequest[ConnectionRequestsTable.fromDeviceId].toString()
        val newStatus = if (request.accept) "ACCEPTED" else "REJECTED"
        var sessionId: String? = null

        transaction {
            ConnectionRequestsTable.update({ ConnectionRequestsTable.id eq UUID.fromString(request.requestId) }) {
                it[status] = newStatus
                it[respondedAt] = LocalDateTime.now()
            }

            if (request.accept) {
                val newSessionId = UUID.randomUUID().toString()
                sessionId = newSessionId
                SessionsTable.insert {
                    it[SessionsTable.id] = UUID.fromString(newSessionId)
                    it[controllerDeviceId] = UUID.fromString(fromDeviceId)
                    it[controlledDeviceId] = UUID.fromString(toDeviceId)
                    it[SessionsTable.status] = "ACTIVE"
                    it[startedAt] = LocalDateTime.now()
                    it[createdAt] = LocalDateTime.now()
                }
            }
        }

        if (request.accept && sessionId != null) {
            // Let the WebSocket layer know these two devices may now exchange screen frames /
            // input events for this session, then tell the original requester the session is live.
            webSocketService.registerSession(sessionId!!, fromDeviceId, toDeviceId)
            webSocketService.sendToDevice(
                fromDeviceId,
                WebSocketMessage.ConnectionAccepted(requestId = request.requestId, sessionId = sessionId!!)
            )
        } else {
            webSocketService.sendToDevice(
                fromDeviceId,
                WebSocketMessage.ConnectionRejected(requestId = request.requestId)
            )
        }

        return ConnectionRequestDto(
            id = request.requestId,
            fromDeviceId = fromDeviceId,
            fromDeviceName = "",
            toDeviceId = toDeviceId,
            status = newStatus,
            createdAt = connectionRequest[ConnectionRequestsTable.createdAt]
                .toEpochSecond(java.time.ZoneOffset.UTC) * 1000,
            sessionId = sessionId
        )
    }

    /** [userId] must own [deviceId]; previously any authenticated user could read any device's pending requests. */
    fun getPendingRequests(userId: String, deviceId: String): List<ConnectionRequestDto> {
        if (!deviceService.isOwnedBy(deviceId, userId)) {
            throw ConnectionAuthorizationException("deviceId does not belong to the authenticated user")
        }

        return transaction {
            ConnectionRequestsTable.select {
                (ConnectionRequestsTable.toDeviceId eq UUID.fromString(deviceId)) and
                (ConnectionRequestsTable.status eq "PENDING")
            }.map { row ->
                val fromDevice = DevicesTable.select { DevicesTable.id eq row[ConnectionRequestsTable.fromDeviceId] }.singleOrNull()
                ConnectionRequestDto(
                    id = row[ConnectionRequestsTable.id].toString(),
                    fromDeviceId = row[ConnectionRequestsTable.fromDeviceId].toString(),
                    fromDeviceName = fromDevice?.get(DevicesTable.name) ?: "Unknown",
                    toDeviceId = row[ConnectionRequestsTable.toDeviceId].toString(),
                    status = row[ConnectionRequestsTable.status],
                    createdAt = row[ConnectionRequestsTable.createdAt].toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                )
            }
        }
    }

    /** [userId] must own one of the two devices in the session. */
    suspend fun disconnectSession(userId: String, sessionId: String) {
        val session = transaction {
            SessionsTable.select { SessionsTable.id eq UUID.fromString(sessionId) }.singleOrNull()
        } ?: throw IllegalArgumentException("Session not found")

        val controllerDeviceId = session[SessionsTable.controllerDeviceId].toString()
        val controlledDeviceId = session[SessionsTable.controlledDeviceId].toString()

        if (!deviceService.isOwnedBy(controllerDeviceId, userId) && !deviceService.isOwnedBy(controlledDeviceId, userId)) {
            throw ConnectionAuthorizationException("You are not a participant in this session")
        }

        transaction {
            SessionsTable.update({ SessionsTable.id eq UUID.fromString(sessionId) }) {
                it[status] = "DISCONNECTED"
                it[endedAt] = LocalDateTime.now()
            }
        }

        webSocketService.forwardToPeer(
            senderDeviceId = if (deviceService.isOwnedBy(controllerDeviceId, userId)) controllerDeviceId else controlledDeviceId,
            sessionId = sessionId,
            message = WebSocketMessage.Disconnect(sessionId = sessionId, reason = "ended_by_peer")
        )
        webSocketService.endSession(sessionId)
    }
}
