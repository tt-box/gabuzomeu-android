package eu.ttbox.gabuzomeu.core.shadok

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Table de référence de la conversion Shadok — voir `docs/shadok-reference.md`.
 *
 * Les valeurs ici sont la vérité du projet. Elles ne reprennent pas telles quelles les
 * assertions des tests d'origine : celles-ci étaient soit absentes (le test
 * `GabuzomeuConverterTest.testEncode` ne faisait que journaliser), soit fausses (voir
 * [decimales fausses dans l'ancien test][fractionsThatTheLegacyCodeGotWrong]).
 */
class ShadokConverterTest {

    private fun labelsOf(value: Rational): String =
        ShadokFormatter.format(ShadokConverter.toBase4(value), ShadokNotation.LABELS)

    private fun base4Of(value: Rational): String =
        ShadokFormatter.format(ShadokConverter.toBase4(value), ShadokNotation.BASE4)

    // ---------------------------------------------------------------- entiers

    @ParameterizedTest(name = "{0} en decimal = {1} en base 4 = {2}")
    @CsvSource(
        "0,      0,      Ga",
        "1,      1,      Bu",
        "2,      2,      Zo",
        "3,      3,      Meu",
        "4,      10,     BuGa",
        "5,      11,     BuBu",
        "6,      12,     BuZo",
        "7,      13,     BuMeu",
        "8,      20,     ZoGa",
        "15,     33,     MeuMeu",
        "16,     100,    BuGaGa",
        "42,     222,    ZoZoZo",
        "63,     333,    MeuMeuMeu",
        "64,     1000,   BuGaGaGa",
        "255,    3333,   MeuMeuMeuMeu",
        "1000,   33220,  MeuMeuZoZoGa",
    )
    fun integers(decimal: Int, expectedBase4: String, expectedLabels: String) {
        val value = Rational.of(decimal)
        assertEquals(expectedBase4, base4Of(value), "ecriture base 4 de $decimal")
        assertEquals(expectedLabels, labelsOf(value), "noms Shadok de $decimal")
    }

    @Test
    fun `le signe est porte par un champ dedie, jamais par un chiffre`() {
        val minusSix = ShadokConverter.toBase4(Rational.of(-6))

        assertTrue(minusSix.negative)
        assertEquals(listOf(ShadokDigit.BU, ShadokDigit.ZO), minusSix.integerDigits)
        assertEquals("−BuZo", ShadokFormatter.format(minusSix, ShadokNotation.LABELS))
    }

    @Test
    fun `zero n'est jamais negatif`() {
        val zero = ShadokConverter.toBase4(Rational.ZERO)

        assertFalse(zero.negative)
        assertEquals("Ga", ShadokFormatter.format(zero, ShadokNotation.LABELS))
        assertTrue(zero.isZero)
    }

    @Test
    fun `les tres grands entiers passent par BigInteger sans debordement`() {
        // 4^40, soit un nombre bien au-dela de Long.MAX_VALUE.
        val big = Rational.of(BigInteger.valueOf(4).pow(40))

        // 4^40 en base 4 = un 1 suivi de 40 zeros.
        assertEquals("Bu" + "Ga".repeat(40), labelsOf(big))
    }

    // ------------------------------------------------- parties fractionnaires

    @ParameterizedTest(name = "{0} = {1} en base 4 = {2}")
    @CsvSource(
        "0.25,   0.1,    Ga.Bu",
        "0.5,    0.2,    Ga.Zo",
        "0.75,   0.3,    Ga.Meu",
        "0.0625, 0.01,   Ga.GaBu",
        "0.125,  0.02,   Ga.GaZo",
        "1.5,    1.2,    Bu.Zo",
        "2.75,   2.3,    Zo.Meu",
        "6.25,   12.1,   BuZo.Bu",
    )
    fun terminatingFractions(decimal: String, expectedBase4: String, expectedLabels: String) {
        val value = Rational.ofDecimal(decimal)

        assertEquals(expectedBase4, base4Of(value), "ecriture base 4 de $decimal")
        assertEquals(expectedLabels, labelsOf(value), "noms Shadok de $decimal")
        assertFalse(
            ShadokConverter.toBase4(value).approximate,
            "$decimal a un developpement fini en base 4",
        )
    }

    /**
     * Le bug central du code d'origine : la partie fractionnaire était convertie comme
     * un entier. `0.5` donnait `Ga.BuBu`, car 5₁₀ = 11₄.
     *
     * Et l'attendu du test d'époque était faux lui aussi : il affirmait `Ga.Bu`, or
     * `Bu` = 1 donc `Ga.Bu` vaut 1×4⁻¹ = **0.25**. La bonne réponse pour 0.5 est
     * `Ga.Zo` (2×4⁻¹).
     */
    @Test
    fun fractionsThatTheLegacyCodeGotWrong() {
        assertEquals("Ga.Zo", labelsOf(Rational.ofDecimal("0.5")))
        assertEquals("Ga.Bu", labelsOf(Rational.ofDecimal("0.25")))

        // La preuve arithmetique : les deux ecritures ne valent pas la meme chose.
        val gaBu = ShadokParser.parseGlyphsOrNull("◯._")!!
        val gaZo = ShadokParser.parseGlyphsOrNull("◯.⅃")!!
        assertEquals(Rational.ofDecimal("0.25"), ShadokConverter.toRational(gaBu))
        assertEquals(Rational.ofDecimal("0.5"), ShadokConverter.toRational(gaZo))
    }

    @Test
    fun `un tiers a un developpement periodique, tronque et signale`() {
        val oneThird = Rational.of(BigInteger.ONE, BigInteger.valueOf(3))
        val converted = ShadokConverter.toBase4(oneThird, maxFractionDigits = 6)

        // 1/3 = 0.111...  en base 4
        assertTrue(converted.approximate, "1/3 ne termine pas en base 4")
        assertEquals("0.111111", ShadokFormatter.format(converted, ShadokNotation.BASE4, false))
        assertEquals(
            "≈Ga.BuBuBuBuBuBu",
            ShadokFormatter.format(converted, ShadokNotation.LABELS),
        )
    }

    @Test
    fun `un dixieme decimal ne termine pas en base 4`() {
        val oneTenth = Rational.ofDecimal("0.1")
        val converted = ShadokConverter.toBase4(oneTenth, maxFractionDigits = 8)

        // 0.1 = 0.01212121|2... en base 4 (periode « 12 ») : le neuvieme chiffre, 2, vaut
        // la moitie, le huitieme est donc arrondi au-dessus.
        assertTrue(converted.approximate)
        assertEquals("0.01212122", ShadokFormatter.format(converted, ShadokNotation.BASE4, false))
    }

    /**
     * Comme 4 = 2², une fraction irréductible termine en base 4 si et seulement si son
     * dénominateur est une puissance de deux.
     */
    @ParameterizedTest(name = "1/{0} termine en base 4 : {1}")
    @CsvSource(
        "2,  true",
        "4,  true",
        "8,  true",
        "16, true",
        "32, true",
        "3,  false",
        "5,  false",
        "6,  false",
        "7,  false",
        "10, false",
    )
    fun terminationDependsOnPowerOfTwoDenominator(denominator: Int, shouldTerminate: Boolean) {
        val value = Rational.of(BigInteger.ONE, BigInteger.valueOf(denominator.toLong()))
        val converted = ShadokConverter.toBase4(value, maxFractionDigits = 64)

        assertEquals(
            shouldTerminate,
            !converted.approximate,
            "1/$denominator devrait ${if (shouldTerminate) "" else "ne pas "}terminer",
        )
    }

    // --------------------------------------------------------- aller-retours

    @Test
    fun `tout entier survit a un aller-retour`() {
        // Graine fixe : un echec est reproductible.
        val random = java.util.Random(SEED)
        repeat(500) { index ->
            val magnitude = BigInteger(64, random)
            // Une moitie de negatifs : le champ « negative » doit aussi survivre.
            val value = Rational.of(if (index % 2 == 0) magnitude else magnitude.negate())
            val roundTripped = ShadokConverter.toRational(ShadokConverter.toBase4(value))
            assertEquals(value, roundTripped, "aller-retour de $value")
        }
    }

    @Test
    fun `toute fraction de denominateur puissance de 4 survit a un aller-retour`() {
        val random = java.util.Random(SEED)
        repeat(500) {
            val exponent = random.nextInt(1, 12)
            val numerator = BigInteger.valueOf(random.nextLong(1, 1_000_000))
            val value = Rational.of(numerator, BigInteger.valueOf(4).pow(exponent))

            val converted = ShadokConverter.toBase4(value, maxFractionDigits = 32)
            assertFalse(converted.approximate, "$value devrait etre exact")
            assertEquals(value, ShadokConverter.toRational(converted), "aller-retour de $value")
        }
    }

    @Test
    fun `les nombres negatifs survivent a un aller-retour`() {
        listOf("-6", "-0.25", "-1000.5", "-1").forEach { text ->
            val value = Rational.ofDecimal(text)
            assertEquals(
                value,
                ShadokConverter.toRational(ShadokConverter.toBase4(value)),
                "aller-retour de $text",
            )
        }
    }

    // ------------------------------------------------------------ robustesse

    @Test
    fun `un chiffre hors base est refuse explicitement, pas par un NPE`() {
        // Le code d'origine passait par un SparseArray renvoyant null, auto-deballe en
        // char : un NullPointerException opaque.
        val failure = assertThrows<IllegalArgumentException> { ShadokDigit.of(4) }
        assertTrue(failure.message!!.contains("4"))

        assertThrows<IllegalArgumentException> { ShadokDigit.of(-1) }
    }

    @Test
    fun `maxFractionDigits negatif est refuse`() {
        assertThrows<IllegalArgumentException> {
            ShadokConverter.toBase4(Rational.ONE, maxFractionDigits = -1)
        }
    }

    @Test
    fun `maxFractionDigits a zero arrondit a l'entier le plus proche`() {
        val converted = ShadokConverter.toBase4(Rational.ofDecimal("0.5"), maxFractionDigits = 0)

        assertTrue(converted.approximate)
        assertTrue(converted.fractionDigits.isEmpty())
        assertEquals("≈Bu", ShadokFormatter.format(converted, ShadokNotation.LABELS))
    }

    @Test
    fun `le dernier chiffre est arrondi, pas tronque`() {
        // 2/3 = 0.2222...|2 en base 4 : le chiffre suivant (2) vaut la moitie, on arrondit.
        val twoThirds = Rational.of(BigInteger.TWO, BigInteger.valueOf(3))
        val converted = ShadokConverter.toBase4(twoThirds, maxFractionDigits = 4)

        assertTrue(converted.approximate)
        assertEquals("0.2223", ShadokFormatter.format(converted, ShadokNotation.BASE4, false))
    }

    @Test
    fun `l'arrondi propage sa retenue jusqu'a la partie entiere`() {
        // 0.3333|3 en base 4 s'arrondit a 1.
        val almostOne = Rational.of(BigInteger.valueOf(1023), BigInteger.valueOf(1024))
        val converted = ShadokConverter.toBase4(almostOne, maxFractionDigits = 4)

        assertTrue(converted.approximate)
        assertEquals("1.0000", ShadokFormatter.format(converted, ShadokNotation.BASE4, false))
    }

    @Test
    fun `un negatif minuscule ne s'affiche pas moins zero`() {
        val tiny = -Rational.of(BigInteger.ONE, BigInteger.valueOf(1_000_000_000_000L))
        val converted = ShadokConverter.toBase4(tiny, maxFractionDigits = 4)

        assertFalse(converted.negative)
        assertEquals("≈0.0000", ShadokFormatter.format(converted, ShadokNotation.BASE4))
    }

    private companion object {
        /** Graine fixe : un échec des tests aléatoires est reproductible. */
        const val SEED = 20260731L
    }
}
