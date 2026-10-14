package za.callguard.core

/** What CallGuard knows about one phone number. */
data class LookupResult(
    val number: String,                 // E.164, e.g. +27821234567
    val verdict: String,                // spam | suspicious | unknown | blocked
    val score: Int = 0,                 // 0-100 spam likelihood
    val reports: Int = 0,
    val notSpam: Int = 0,
    val category: String? = null,       // scam, telemarketing, ...
    val label: String? = null,          // community label, e.g. "Fake bank call"
    val businessName: String? = null,   // verified business only
    val carrier: String? = null,
    val location: String? = null,
    val lineType: String? = null,
    val source: String = "server",      // server | offline cache | your block list | none
    val myName: String? = null,         // a name you saved for this number (on this phone only)
) {
    val isSpam get() = verdict == "spam"
    val isBlockedByUser get() = verdict == "blocked"

    /** Best name to show on the incoming-call notification. */
    fun title(): String = myName
        ?: businessName
        ?: label
        ?: when (verdict) {
            "spam" -> "Likely spam"
            "suspicious" -> "Suspicious caller"
            "blocked" -> "Blocked number"
            else -> PhoneUtil.pretty(number)
        }

    fun subtitle(): String {
        val parts = mutableListOf<String>()
        if (verdict == "spam" || verdict == "suspicious") {
            parts += "$score% spam · $reports report${if (reports == 1) "" else "s"}"
            category?.let { parts += it.replace('_', ' ') }
        }
        if (title() != PhoneUtil.pretty(number)) parts += PhoneUtil.pretty(number)
        listOfNotNull(carrier, location).distinct().takeIf { it.isNotEmpty() }
            ?.let { parts += it.joinToString(" · ") }
        return parts.joinToString(" · ")
    }

    companion object {
        fun unknown(number: String) = LookupResult(number, "unknown", source = "none")
    }
}

data class ScreenedCall(
    val id: Long,
    val number: String,
    val title: String,
    val verdict: String,
    val action: String,   // allowed | blocked
    val at: Long,
)

data class BlockedNumber(val number: String, val label: String?, val at: Long)

val REPORT_CATEGORIES = listOf(
    "scam", "telemarketing", "robocall", "debt_collector", "survey", "harassment", "other",
)
