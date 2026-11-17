package com.codewave.qullqi.data

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataTest {
    private val t = testDatabase()
    private val tokens = com.codewave.qullqi.data.remote.FakeTokenStore()
    private val reset = LocalDataReset(t.db, tokens)
    private val tables = listOf("user", "credential", "current_session", "terms_acceptance", "phone_confirmation")

    /** Every row of every table as text, in a stable order. */
    private fun dump(): String = tables.joinToString("\n") { table ->
        t.driver.executeQuery(null, "SELECT * FROM $table ORDER BY 1", { c ->
            val rows = mutableListOf<String>()
            // SqlCursor has no column count; read past the end (null) — widest table has 11 columns.
            while (c.next().value) rows += (0 until 11).joinToString("|") { i -> runCatching { c.getString(i) }.getOrNull().toString() }
            QueryResult.Value(rows)
        }, 0).value.joinToString("\n", prefix = "[$table]\n")
    }

    private fun strings(sql: String): List<String> =
        t.driver.executeQuery(null, sql, { c ->
            val out = mutableListOf<String>()
            while (c.next().value) out += c.getString(0)!!
            QueryResult.Value(out)
        }, 0).value

    @Test fun exactSampleUsers() {
        assertEquals(
            listOf("usr-locked", "usr-new", "usr-rejected", "usr-review", "usr-unverified", "usr-verified"),
            strings("SELECT id FROM user ORDER BY id"),
        )
    }


    @Test fun resetIsDeterministic() {
        val first = dump()
        repeat(10) {
            t.exec("INSERT INTO current_session (slot, user_id, opened_at) VALUES (1, 'usr-new', 1)")
            t.db.seedQueries.deleteAll()
            t.db.seedQueries.insertSamples()
            assertEquals(first, dump())
        }
    }

    @Test fun emptyDevice() {
        tokens.values["usr-verified"] = "rt"
        t.exec("INSERT INTO device (slot, device_id) VALUES (1, 'dev-1')")
        reset.reset()
        assertEquals(0, t.count("SELECT count(*) FROM user"))
        assertEquals(0, t.count("SELECT count(*) FROM device"))
        assertTrue(tokens.values.isEmpty())
    }

    @Test fun onlySyntheticData() {
        assertTrue(strings("SELECT phone FROM user").all { Regex("""\+59170000\d{3}""").matches(it) })
        assertTrue(strings("SELECT email FROM user").all { it.endsWith("@example.com") })
    }
}
