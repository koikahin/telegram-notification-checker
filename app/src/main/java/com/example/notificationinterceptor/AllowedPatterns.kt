package com.example.notificationinterceptor


val DISALLOWED_PATTERNS = arrayOf(
    """\bNA\b""",
    """\bno all""",
    """\bNOT AVAIL""",
    """\bnone avail""",
    """\bno slots avail""",
    """\bcrypto""",
    """\bmoney\b""",
    """\$[0-9]+""",
    """\$\$+""",
    """@AwesomeAdmin_US""",
    """@blackwidow""",
    """http://""",
    """https://""",
    """ping me""",
    """contact me""",
    """fake""",
    """money"""
)

val PRIORITY_PATTERNS = arrayOf(
    """\bbulk\b""",
    """\b(several|many|lot)"""
)

private val DISALLOWED_REGEXES = DISALLOWED_PATTERNS.map { Regex(it, RegexOption.IGNORE_CASE) }
private val PRIORITY_REGEXES = PRIORITY_PATTERNS.map { Regex(it, RegexOption.IGNORE_CASE) }

private fun String.matchesAny(regexes: List<Regex>) = regexes.any { it.containsMatchIn(this) }
fun String.matchesDisallowed() = matchesAny(DISALLOWED_REGEXES)
fun String.matchesPriority() = matchesAny(PRIORITY_REGEXES)

fun String.isPriority(): Boolean {
    return this.matchesPriority()
}

