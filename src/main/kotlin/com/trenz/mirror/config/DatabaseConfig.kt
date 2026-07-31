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

    // Plain SQLite defaults to a rollback journal, where a writer blocks ALL readers until it
    // finishes - with several background refresh loops all polling the same database file at
    // once, this caused real multi-second delays under load (confirmed in production logs: a
    // simple pairing-accept request took 5-11 seconds to respond). WAL mode lets reads proceed
    // concurrently with a single writer instead of blocking; busy_timeout makes any operation
    // that still needs to wait for the write lock retry for a bit instead of failing immediately.
    transaction {
        exec("PRAGMA journal_mode=WAL;")
        exec("PRAGMA busy_timeout=5000;")
    }

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
