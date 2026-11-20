package com.codewave.qullqi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Integrity is enforced by the store itself (spec 002, FR-021, SC-004). */
class SchemaIntegrityTest {
    // Spec 005: the local KYC table (kyc_attempt) is gone; verification lives in the backend.
    private val t = testDatabase()
    private val now = 1_767_225_600_000L

    private fun userInsert(id: String, phone: String) =
        "INSERT INTO user (id, full_name, phone, email, created_at) VALUES ('$id', 'Test', '$phone', 't@example.com', $now)"

    @Test fun duplicatePhoneRefused() {
        assertFails { t.exec(userInsert("u-dup", "+59170000001")) }
    }

    @Test fun nonBolivianMobileRefused() {
        assertFails { t.exec(userInsert("u-bad", "+59150000001")) }
        assertFails { t.exec(userInsert("u-bad", "70000009")) }
    }


    @Test fun secondSessionSlotRefused() {
        assertFails { t.exec("INSERT INTO current_session (slot, user_id, opened_at) VALUES (2, 'usr-verified', $now)") }
    }





    @Test fun deletingUserCascades() {
        t.exec("DELETE FROM user WHERE id = 'usr-rejected'")
        assertEquals(0, t.count("SELECT count(*) FROM credential WHERE user_id = 'usr-rejected'"))
        assertEquals(0, t.count("SELECT count(*) FROM terms_acceptance WHERE user_id = 'usr-rejected'"))
    }

}
