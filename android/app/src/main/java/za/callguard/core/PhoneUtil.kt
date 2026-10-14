package za.callguard.core

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

object PhoneUtil {
    private val util = PhoneNumberUtil.getInstance()

    /** "082 123 4567" -> "+27821234567". Returns null for hidden / invalid numbers. */
    fun normalize(raw: String?, region: String = "ZA"): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val p = util.parse(raw, region)
            if (util.isPossibleNumber(p)) util.format(p, PhoneNumberUtil.PhoneNumberFormat.E164) else null
        } catch (e: NumberParseException) {
            null
        }
    }

    /**
     * Web search text covering the ways a number is usually written on websites:
     * "021 444 0000" OR "0214440000" OR "+27214440000" OR "+27 21 444 0000"
     */
    fun searchQuery(e164: String): String = try {
        val p = util.parse(e164, "ZA")
        val national = util.format(p, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        val digits = national.filter { it.isDigit() }
        val intl = util.format(p, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        listOf(national, digits, e164, intl).distinct().joinToString(" OR ") { "\"$it\"" }
    } catch (e: NumberParseException) {
        "\"$e164\""
    }

    /** "+27821234567" -> "+27 82 123 4567" */
    fun pretty(e164: String): String = try {
        util.format(util.parse(e164, "ZA"), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        e164
    }
}
