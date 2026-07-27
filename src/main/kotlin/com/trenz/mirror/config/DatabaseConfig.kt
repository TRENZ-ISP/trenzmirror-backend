package com.trenz.mirror.config

import com.trenz.mirror.database.tables.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val dbPath = environment.config.propertyOrNull("database.path")?.getString() 
        ?: "jdbc:sqlite:trenz_mirror.db"

    Database.connect(
        url = dbPath,
        driver = "org.sqlite.JDBC"
    )

    transaction {
        // createMissingTablesAndColumns (not plain create) - now that Railway has a persistent
        // volume with real user data, we need ALTER TABLE for new columns on existing tables
        // (like DevicesTable's new pairing fields), not just CREATE TABLE for brand-new ones.
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            DevicesTable,
            SessionsTable,
            PairRequestsTable,
            PairedDevicesTable,
            ConnectionRequestsTable,
            RefreshTokensTable
        )
    }
}
