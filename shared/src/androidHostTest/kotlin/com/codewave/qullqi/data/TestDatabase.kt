package com.codewave.qullqi.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.codewave.qullqi.data.db.DatabaseFactory
import com.codewave.qullqi.data.db.SeededSchema
import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.IdGenerator

class TestDb(val driver: SqlDriver, val db: QullqiDatabase) {
    fun exec(sql: String) {
        driver.execute(null, sql, 0)
    }

    fun count(sql: String): Long =
        driver.executeQuery(null, sql, { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0)!! else 0L) }, 0).value
}

/** In-memory database with the real schema, foreign keys on, and optionally the sample data. */
fun testDatabase(withSamples: Boolean = true): TestDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    if (withSamples) SeededSchema.create(driver) else QullqiDatabase.Schema.create(driver)
    return TestDb(driver, DatabaseFactory.create(driver))
}

class FakeClock(var now: Long = 1_767_225_600_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var next = 1
    override fun newId(): String = "$prefix-${next++}"
}
