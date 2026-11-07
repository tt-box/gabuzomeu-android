package eu.ttbox.gabuzomeu.core.shadok

import java.math.BigInteger

/**
 * Conversion entre valeurs exactes ([Rational]) et écriture Shadok ([Base4Number]).
 */
object ShadokConverter {

    /**
     * Nombre maximum de chiffres après la virgule produits pour un développement qui
     * ne termine pas. 16 chiffres en base 4 = 32 bits de partie fractionnaire, bien
     * au-delà de ce qu'un écran de calculatrice affiche.
     */
    const val DEFAULT_MAX_FRACTION_DIGITS: Int = 16

    private val RADIX = BigInteger.valueOf(ShadokDigit.RADIX.toLong())
    private val RADIX_RATIONAL = Rational.of(ShadokDigit.RADIX)
    private val HALF = Rational.of(BigInteger.ONE, BigInteger.TWO)

    /**
     * Écrit [value] en base 4 Shadok.
     *
     * La partie entière passe par [BigInteger.toString] en base 4. La partie
     * fractionnaire — **absente du code d'origine** — s'obtient par multiplications
     * successives par 4 : à chaque tour, la partie entière du produit donne le chiffre
     * suivant et l'on continue avec le reste.
     *
     * Exemple, 0.5 : 0.5 × 4 = 2.0 → chiffre `2` (Zo), reste 0 → `Ga.Zo`.
     * Le code d'origine produisait `Ga.BuBu`, en convertissant « 5 » comme un entier
     * (5₁₀ = 11₄). Le test d'époque attendait `Ga.Bu`, ce qui est faux aussi : `Ga.Bu`
     * vaut 1×4⁻¹ = 0.25.
     */
    fun toBase4(
        value: Rational,
        maxFractionDigits: Int = DEFAULT_MAX_FRACTION_DIGITS,
    ): Base4Number {
        require(maxFractionDigits >= 0) { "maxFractionDigits négatif : $maxFractionDigits" }

        val magnitude = value.abs()
        val integerPart = magnitude.truncate()

        val integerDigits = integerPart.toString(ShadokDigit.RADIX)
            .map { ShadokDigit.of(it.digitToInt(ShadokDigit.RADIX)) }

        // `fractionalPart()` plutôt que la soustraction écrite à la main : c'est la même
        // opération, nommée une seule fois et vérifiée par les tests de [Rational].
        var remainder = magnitude.fractionalPart()
        val fractionDigits = ArrayList<ShadokDigit>(maxFractionDigits)
        while (!remainder.isZero && fractionDigits.size < maxFractionDigits) {
            remainder *= RADIX_RATIONAL
            val digit = remainder.truncate()
            fractionDigits += ShadokDigit.of(digit.toInt())
            remainder -= Rational.of(digit)
        }

        val approximate = !remainder.isZero
        // Arrondi au plus proche, la moitié vers le haut : 2/3 à quatre chiffres s'écrit
        // 0.2223 et non 0.2222. Le reste est dans [0, 1[ en unités du dernier chiffre.
        val rounded = if (approximate && remainder >= HALF) {
            roundUp(integerDigits, fractionDigits)
        } else {
            integerDigits to fractionDigits
        }

        return Base4Number(
            // Négatif seulement si un chiffre non nul est écrit : signum() == 0 pour zéro,
            // et un négatif minuscule arrondi à zéro ne doit pas s'afficher « −Ga.GaGa ».
            negative = value.signum() < 0 &&
                (rounded.first + rounded.second).any { it != ShadokDigit.GA },
            integerDigits = rounded.first,
            fractionDigits = rounded.second,
            approximate = approximate,
        )
    }

    /**
     * Ajoute une unité au dernier chiffre écrit, retenue comprise, jusque dans la partie
     * entière (0.3333 + 0.0001 = 1.0000). Les chiffres écrits restent au nombre demandé,
     * comme pour un résultat non arrondi ; l'écriture reste marquée approchée.
     */
    private fun roundUp(
        integerDigits: List<ShadokDigit>,
        fractionDigits: List<ShadokDigit>,
    ): Pair<List<ShadokDigit>, List<ShadokDigit>> {
        val values = (integerDigits + fractionDigits).map { it.value }.toMutableList()
        var index = values.lastIndex
        var carry = true
        while (carry && index >= 0) {
            val sum = values[index] + 1
            carry = sum == ShadokDigit.RADIX
            values[index] = if (carry) 0 else sum
            index--
        }
        if (carry) values.add(0, 1)

        val integerCount = values.size - fractionDigits.size
        val digits = values.map { ShadokDigit.of(it) }
        return digits.take(integerCount) to
            digits.drop(integerCount)
    }

    /**
     * Relit une écriture Shadok en valeur exacte.
     *
     * Pour un [Base4Number] marqué `approximate`, le résultat est la valeur de ce qui
     * a été écrit — donc une approximation de l'original, par construction.
     */
    fun toRational(number: Base4Number): Rational {
        val integer = number.integerDigits
            .fold(BigInteger.ZERO) { acc, digit ->
                acc * RADIX + BigInteger.valueOf(digit.value.toLong())
            }

        // Σ dᵢ·4^-(i+1) = (Σ dᵢ·4^(n-1-i)) / 4ⁿ
        val fractionCount = number.fractionDigits.size
        val result = if (fractionCount == 0) {
            Rational.of(integer)
        } else {
            val scaled = number.fractionDigits
                .fold(BigInteger.ZERO) { acc, digit ->
                    acc * RADIX + BigInteger.valueOf(digit.value.toLong())
                }
            Rational.of(integer) + Rational.of(scaled, RADIX.pow(fractionCount))
        }

        return if (number.negative) -result else result
    }
}
