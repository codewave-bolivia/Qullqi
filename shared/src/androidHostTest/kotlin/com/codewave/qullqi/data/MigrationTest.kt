package com.codewave.qullqi.data

import com.codewave.qullqi.data.remote.deviceId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Schema v2 (spec 004). The v1 → v2 path itself is verified by verifyCommonMainQullqiDatabaseMigration. */
class MigrationTest {
    private val t = testDatabase(withSamples = false)
    private val q = t.db.userQueries

    @Test fun deviceIdIsCreatedOnce() {
        val ids = FakeIdGenerator("dev")
        assertEquals("dev-1", t.db.deviceId(ids))
        assertEquals("dev-1", t.db.deviceId(ids))
        assertEquals(1, t.count("SELECT count(*) FROM device"))
    }

    @Test fun upsertKeepsOneRowAndAcceptsMissingMaternalName() {
        q.upsertUser("u-1", "Ana Paz", "Ana", "Paz", null, "+59170000001", "ana@example.com", "NOT_VERIFIED", 1)
        q.upsertUser("u-1", "Ana Paz", "Ana", "Paz", "Rojas", "+59170000001", "ana@example.com", "VERIFIED", 2)
        assertEquals(1, t.count("SELECT count(*) FROM user"))
        assertEquals(1, t.count("SELECT count(*) FROM user WHERE verification = 'VERIFIED' AND last_name_maternal = 'Rojas' AND created_at = 1"))
    }
}
