package com.codewave.qullqi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Integrity is enforced by the store itself (spec 002, FR-021, SC-004). */
class SchemaIntegrityTest {
    private val t = testDatabase()
    private val now = 1_767_225_600_000L

    private fun userInsert(id: String, phone: String) =
        "INSERT INTO user (id, full_name, phone, email, created_at) VALUES ('$id', 'Test', '$phone', 't@example.com', $now)"

    private fun attemptInsert(id: String, user: String, type: String = "CI", extra: String = "", extraValues: String = "") =
        "INSERT INTO kyc_attempt (id, user_id, document_type, created_at$extra) VALUES ('$id', '$user', '$type', $now$extraValues)"

    @Test fun duplicatePhoneRefused() {
        assertFails { t.exec(userInsert("u-dup", "+59170000001")) }
    }

    @Test fun nonBolivianMobileRefused() {
        assertFails { t.exec(userInsert("u-bad", "+59150000001")) }
        assertFails { t.exec(userInsert("u-bad", "70000009")) }
    }

    @Test fun orphanAttemptRefused() {
        assertFails { t.exec(attemptInsert("a-orphan", "nobody")) }
    }

    @Test fun secondSessionSlotRefused() {
        assertFails { t.exec("INSERT INTO current_session (slot, user_id, opened_at) VALUES (2, 'usr-verified', $now)") }
    }

    @Test fun secondOpenAttemptRefused() {
        t.exec(attemptInsert("a-1", "usr-unverified"))
        assertFails { t.exec(attemptInsert("a-2", "usr-unverified")) }
    }

    @Test fun realImageReferenceRefused() {
        assertFails {
            t.exec(attemptInsert("a-img", "usr-unverified", extra = ", front_ref", extraValues = ", 'file:///photo.jpg'"))
        }
    }

    @Test fun backImageOnPassportRefused() {
        assertFails {
            t.exec(attemptInsert("a-pp", "usr-unverified", "PASSPORT", ", back_ref", ", 'placeholder:ci-back'"))
        }
    }

    @Test fun rejectedWithoutReasonRefused() {
        assertFails {
            t.exec(attemptInsert("a-rej", "usr-unverified", extra = ", state", extraValues = ", 'REJECTED'"))
        }
    }

    @Test fun deletingUserCascades() {
        t.exec("DELETE FROM user WHERE id = 'usr-rejected'")
        assertEquals(0, t.count("SELECT count(*) FROM credential WHERE user_id = 'usr-rejected'"))
        assertEquals(0, t.count("SELECT count(*) FROM terms_acceptance WHERE user_id = 'usr-rejected'"))
        assertEquals(0, t.count("SELECT count(*) FROM kyc_attempt WHERE user_id = 'usr-rejected'"))
    }

    @Test fun finalAttemptUpdatesUserStatusViaTrigger() {
        t.exec(attemptInsert("a-t", "usr-unverified"))
        t.exec("UPDATE kyc_attempt SET state = 'REVIEW' WHERE id = 'a-t'")
        assertEquals(1, t.count("SELECT count(*) FROM user WHERE id = 'usr-unverified' AND verification = 'UNDER_REVIEW'"))
    }
}
