package com.codewave.qullqi.data

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataTest {
    private val t = testDatabase()
    private val reset = LocalDataReset(t.db)
    private val tables = listOf("user", "credential", "current_session", "terms_acceptance", "phone_confirmation", "kyc_attempt")

    /** Every row of every table as text, in a stable order. */
    private fun dump(): String = tables.joinToString("\n") { table ->
        t.driver.executeQuery(null, "SELECT * FROM $table ORDER BY 1", { c ->
            val rows = mutableListOf<String>()
            // SqlCursor has no column count; read past the end (null) — widest table has 16 columns.
            while (c.next().value) rows += (0 until 16).joinToString("|") { i -> runCatching { c.getString(i) }.getOrNull().toString() }
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

    @Test fun statusMatchesLatestFinalAttempt() {
        val mismatches = strings(
            """
            SELECT u.id FROM user u
            LEFT JOIN kyc_attempt a ON a.id = (
              SELECT id FROM kyc_attempt WHERE user_id = u.id AND state IN ('APPROVED','REVIEW','REJECTED')
              ORDER BY created_at DESC LIMIT 1)
            WHERE u.verification <> CASE a.state
              WHEN 'APPROVED' THEN 'VERIFIED' WHEN 'REVIEW' THEN 'UNDER_REVIEW' ELSE 'NOT_VERIFIED' END
            """.trimIndent(),
        )
        assertEquals(emptyList(), mismatches)
    }

    @Test fun resetIsDeterministic() {
        val first = dump()
        repeat(10) {
            t.exec("INSERT INTO current_session (slot, user_id, opened_at) VALUES (1, 'usr-new', 1)")
            reset.reset(withSamples = true)
            assertEquals(first, dump())
        }
    }

    @Test fun emptyDevice() {
        reset.reset(withSamples = false)
        assertEquals(0, t.count("SELECT count(*) FROM user"))
        assertEquals(0, t.count("SELECT count(*) FROM kyc_attempt"))
    }

    @Test fun onlySyntheticData() {
        assertTrue(strings("SELECT phone FROM user").all { Regex("""\+59170000\d{3}""").matches(it) })
        assertTrue(strings("SELECT email FROM user").all { it.endsWith("@example.com") })
        val refs = strings(
            "SELECT front_ref FROM kyc_attempt WHERE front_ref IS NOT NULL UNION ALL " +
                "SELECT back_ref FROM kyc_attempt WHERE back_ref IS NOT NULL UNION ALL " +
                "SELECT selfie_ref FROM kyc_attempt WHERE selfie_ref IS NOT NULL",
        )
        assertTrue(refs.isNotEmpty() && refs.all { it.startsWith("placeholder:") })
    }
}
