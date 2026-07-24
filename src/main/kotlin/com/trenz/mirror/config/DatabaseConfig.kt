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
        SchemaUtils.create(
            UsersTable,
            DevicesTable,
            SessionsTable,
            PairRequestsTable,
            ConnectionRequestsTable,
            RefreshTokensTable
        )
    }
}
