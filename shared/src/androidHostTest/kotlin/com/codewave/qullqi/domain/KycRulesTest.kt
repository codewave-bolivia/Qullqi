package com.codewave.qullqi.domain

import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.documentNumberValid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 005 FR-003 / research R5: dates without a date library, document numbers. */
class KycRulesTest {
    private val today = SimpleDate(2026, 11, 17)

    @Test fun parsesRealCalendarDates() {
        assertEquals(SimpleDate(1992, 4, 15), SimpleDate.parseDigits("15041992"))
        assertNull(SimpleDate.parseDigits("31022020"))
        assertEquals(SimpleDate(2024, 2, 29), SimpleDate.parseDigits("29022024"))
        assertNull(SimpleDate.parseDigits("29022023"))
        assertNull(SimpleDate.parseDigits("29021900")) // not a leap year
        assertNull(SimpleDate.parseDigits("15131992"))
        assertNull(SimpleDate.parseDigits("1504199"))
        assertEquals("1992-04-15", SimpleDate(1992, 4, 15).iso())
    }

    @Test fun adultOnlyFromTheEighteenthBirthday() {
        assertTrue(SimpleDate(2008, 11, 17).isAdultOn(today))
        assertFalse(SimpleDate(2008, 11, 18).isAdultOn(today))
        assertFalse(SimpleDate(2027, 1, 1).isAdultOn(today))
        assertTrue(SimpleDate(1960, 1, 1).isAdultOn(today))
    }

    @Test fun documentNumbers() {
        assertTrue(documentNumberValid("12345"))
        assertTrue(documentNumberValid("A".repeat(15)))
        assertTrue(documentNumberValid(" 7654321 "))
        assertFalse(documentNumberValid("1234"))
        assertFalse(documentNumberValid("1".repeat(16)))
        assertFalse(documentNumberValid("765 4321"))
    }
}
