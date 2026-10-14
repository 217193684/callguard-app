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

    /** "+27821234567" -> "+27 82 123 4567" */
    fun pretty(e164: String): String = try {
        util.format(util.parse(e164, "ZA"), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        e164
    }
}
