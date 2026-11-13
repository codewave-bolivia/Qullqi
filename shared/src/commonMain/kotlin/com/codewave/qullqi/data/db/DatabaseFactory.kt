package com.codewave.qullqi.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.codewave.qullqi.db.QullqiDatabase

/**
 * Builds the database from a platform-provided driver. No expect/actual: Android passes its
 * driver from androidMain; iOS/Desktop drivers come from the Builder (constitution IV).
 */
object DatabaseFactory {
    fun create(driver: SqlDriver): QullqiDatabase {
        // SQLite ships with foreign keys off; every connection must enable them (CLAUDE.md §14.2).
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return QullqiDatabase(driver)
    }
}

/** The generated schema plus the deterministic sample data, created together (CLAUDE.md §14.3). */
object SeededSchema : SqlSchema<QueryResult.Value<Unit>> by QullqiDatabase.Schema {
    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        QullqiDatabase.Schema.create(driver)
        QullqiDatabase(driver).seedQueries.insertSamples()
        return QueryResult.Unit
    }
}
