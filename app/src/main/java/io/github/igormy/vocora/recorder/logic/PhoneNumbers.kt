package io.github.igormy.vocora.recorder.logic

/** How many trailing digits have to agree for two numbers to be the same person. */
private const val SIGNIFICANT_DIGITS = 9

/**
 * Compares phone numbers the way a person would.
 *
 * The same contact reaches you as `+34 654 53 75 43`, `0034654537543` or `654537543` depending on
 * who dialled and how it was saved, so matching is done on the trailing digits rather than on the
 * text. Short numbers, like the 1200 of an operator, have to agree in full.
 */
object PhoneNumbers {

    fun digitsOf(number: String): String = number.filter(Char::isDigit)

    fun matches(one: String, other: String): Boolean {
        val a = digitsOf(one)
        val b = digitsOf(other)
        if (a.isEmpty() || b.isEmpty()) return false

        val length = minOf(a.length, b.length, SIGNIFICANT_DIGITS)
        // Comparing a short number by its tail would make 200 match 1200.
        if (a.length != b.length && length < SIGNIFICANT_DIGITS) return false
        return a.takeLast(length) == b.takeLast(length)
    }

    fun isListed(number: String, among: Collection<String>): Boolean =
        among.any { matches(it, number) }
}
