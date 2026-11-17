package com.codewave.qullqi.domain.model

/**
 * Identity verification (spec 005). Capture happens in the provider's pages (Persona); the status
 * lives in the backend. These types replace the spec 002 local capture model.
 */
enum class DocumentType { CI, PASSPORT }

/** `CREATED` = started, not finished in the provider · `PENDING` = in review. */
enum class KycStatus { CREATED, PENDING, APPROVED, DECLINED }

enum class DeclineReason { DATA_MISMATCH, DOCUMENT, IDENTITY, OTHER }

data class Verification(
    val status: KycStatus,
    /** Provider page, only while [KycStatus.CREATED]. Never stored or logged. */
    val pageUrl: String?,
    val declineReason: DeclineReason?,
)

/** A calendar date without time zone (no date library in shared code, spec 005 research R5). */
data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
    fun iso(): String = "${year.toString().padStart(4, '0')}-${month.pad()}-${day.pad()}"

    override fun compareTo(other: SimpleDate): Int =
        compareValuesBy(this, other, SimpleDate::year, SimpleDate::month, SimpleDate::day)

    /** Born on this date, 18 or older on [today] (birthday reached). */
    fun isAdultOn(today: SimpleDate): Boolean {
        if (this >= today) return false
        val eighteenth = SimpleDate(year + ADULT_AGE, month, day)
        return eighteenth <= today
    }

    companion object {
        const val ADULT_AGE = 18

        /** "DDMMYYYY" → a real calendar date, or null (month 1–12, valid day, Feb 29 only in leap years). */
        fun parseDigits(digits: String): SimpleDate? {
            if (digits.length != 8 || !digits.all(Char::isDigit)) return null
            val day = digits.substring(0, 2).toInt()
            val month = digits.substring(2, 4).toInt()
            val year = digits.substring(4).toInt()
            if (month !in 1..12 || day < 1 || day > daysIn(month, year)) return null
            return SimpleDate(year, month, day)
        }

        private fun daysIn(month: Int, year: Int) = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun Int.pad() = toString().padStart(2, '0')
    }
}

private val DOCUMENT_NUMBER = Regex("^[A-Za-z0-9]{5,15}$")

/** 5–15 letters or digits, no spaces (spec 005 FR-003). */
fun documentNumberValid(number: String): Boolean = DOCUMENT_NUMBER.matches(number.trim())
