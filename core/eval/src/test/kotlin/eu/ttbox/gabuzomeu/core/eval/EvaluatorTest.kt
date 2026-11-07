package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluatorTest {

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "2+3,          5",
        "10-4,         6",
        "6*7,          42",
        "84/2,         42",
        // Priorite des operateurs
        "2+3*4,        14",
        "2*3+4,        10",
        "10-2*3,       4",
        "100/10/2,     5",
        "10-5-2,       3",
        // Parentheses
        "(2+3)*4,      20",
        "2*(3+4),      14",
        "((2+3)),      5",
        // Moins unaire
        "-5,           -5",
        "-5+3,         -2",
        "-(2+3),       -5",
        "2*-3,         -6",
        // L'exception de la regle 3
        "5+-3,         2",
        // Decimales
        "0.5+0.25,     0.75",
        "1.5*2,        3",
    )
    fun arithmetic(keys: String, expected: String) {
        assertEquals(Rational.ofDecimal(expected), successOf(keys), "evaluation de $keys")
    }

    @Test
    fun `l'arithmetique est exacte, pas flottante`() {
        // En double, 0.1+0.2 != 0.3. En Rational, l'egalite est vraie.
        assertEquals(Rational.ofDecimal("0.3"), successOf("0.1+0.2"))

        // Un tiers reste exactement un tiers, et non 0.3333333.
        val oneThird = successOf("1/3")
        assertEquals(Rational.of(BigInteger.ONE, BigInteger.valueOf(3)), oneThird)
        // Donc trois tiers font exactement un.
        assertEquals(Rational.ONE, oneThird * Rational.of(3))
    }

    @Test
    fun `un tiers se rend en base 4 comme un developpement periodique`() {
        // C'est tout l'interet de l'arithmetique exacte : 1/3 = 0.111... en base 4.
        val converted = ShadokConverter.toBase4(successOf("1/3"), maxFractionDigits = 5)

        assertTrue(converted.approximate)
        assertEquals(
            "0.11111",
            ShadokFormatter.format(converted, ShadokNotation.BASE4, markApproximation = false),
        )
    }

    // -------------------------------------------------------------- erreurs

    @Test
    fun `la division par zero est une erreur, pas un crash`() {
        assertEquals(EvalError.DIVISION_BY_ZERO, failureOf("5/0"))
        assertEquals(EvalError.DIVISION_BY_ZERO, failureOf("5/(3-3)"))
    }

    @Test
    fun `une expression vide est signalee`() {
        assertEquals(EvalError.EMPTY, failureOf(""))
        // « + » seul est refuse a la saisie, donc le tampon reste vide.
        assertEquals(EvalError.EMPTY, failureOf("+"))
    }

    @Test
    fun `un operateur en attente d'operande est ignore`() {
        // Appuyer sur « = » apres « 5+ » donne 5, comme dans le code d'origine.
        assertEquals(Rational.of(5), successOf("5+"))
        assertEquals(Rational.of(5), successOf("5*"))
    }

    @Test
    fun `les parentheses laissees ouvertes sont fermees automatiquement`() {
        assertEquals(Rational.of(20), successOf("(2+3)*4"))
        // « (2+3 » vaut 5 : la fermante manquante est ajoutee.
        assertEquals(Rational.of(5), successOf("(2+3"))
        assertEquals(Rational.of(9), successOf("((2+3)+4"))
    }

    @Test
    fun `un moins seul n'est pas evaluable`() {
        // Le moins final est retire, il ne reste rien.
        assertEquals(EvalError.EMPTY, failureOf("-"))
    }

    // ------------------------------------------------ le cas Shadok de bout en bout

    @Test
    fun `MEU fois ZO donne BuZo`() {
        // 3 x 2 = 6, et 6 en base 4 s'ecrit 12, soit Bu Zo.
        val result = successOf("◿×⅃", NumberNotation.SHADOK)

        assertEquals(Rational.of(6), result)
        assertEquals(
            "BuZo",
            ShadokFormatter.format(ShadokConverter.toBase4(result), ShadokNotation.LABELS),
        )
    }

    @Test
    fun `MeuMeu plus Bu donne BuGaGa`() {
        // 15 + 1 = 16, et 16 en base 4 s'ecrit 100, soit Bu Ga Ga.
        val result = successOf("◿◿+_", NumberNotation.SHADOK)

        assertEquals(Rational.of(16), result)
        assertEquals(
            "BuGaGa",
            ShadokFormatter.format(ShadokConverter.toBase4(result), ShadokNotation.LABELS),
        )
    }

    @Test
    fun `une division Shadok exacte reste exacte`() {
        // BuGa (4) divise par ⅃ (2) = ⅃ (2).
        val result = successOf("_◯÷⅃", NumberNotation.SHADOK)

        assertEquals(Rational.of(2), result)
        assertEquals(
            "Zo",
            ShadokFormatter.format(ShadokConverter.toBase4(result), ShadokNotation.LABELS),
        )
    }

    // -------------------------------------------------------------- utilitaires

    @Test
    fun `une parenthese ouvrante en attente est ignoree comme un operateur`() {
        // « 2×( » puis « = » : rien n'a ete ecrit dans la parenthese, elle est abandonnee
        // avec l'operateur qui la precede, au lieu de rendre l'expression invalide.
        assertEquals(Rational.of(2), successOf("2*("))
        assertEquals(Rational.of(2), successOf("2*(-"))
        assertEquals(Rational.of(2), successOf("2*((("))
        assertEquals(EvalError.EMPTY, failureOf("(("))
    }

    @Test
    fun `une imbrication demesuree est une erreur de syntaxe, pas un debordement de pile`() {
        val deep = ExpressionBuffer(
            atoms = List(20_000) { Atom.LeftParen } + Atom.Number(NumberNotation.DECIMAL, "1"),
        )

        assertEquals(EvalResult.Failure(EvalError.SYNTAX), Evaluator.evaluate(deep))
    }

    @Test
    fun `une imbrication raisonnable reste calculee`() {
        val depth = Parser.MAX_DEPTH - 1
        val keys = "(".repeat(depth) + "7" + ")".repeat(depth)

        assertEquals(Rational.of(7), successOf(keys))
    }

    private fun successOf(
        keys: String,
        notation: NumberNotation = NumberNotation.DECIMAL,
    ): Rational {
        val result = Evaluator.evaluate(type(keys, notation))
        assertTrue(result is EvalResult.Success, "$keys devrait s'evaluer, obtenu $result")
        return result.value
    }

    private fun failureOf(
        keys: String,
        notation: NumberNotation = NumberNotation.DECIMAL,
    ): EvalError {
        val result = Evaluator.evaluate(type(keys, notation))
        assertTrue(result is EvalResult.Failure, "$keys devrait echouer, obtenu $result")
        return result.error
    }

    private fun type(keys: String, notation: NumberNotation): ExpressionBuffer {
        var buffer = ExpressionBuffer(notation)
        keys.forEach { key ->
            buffer = when {
                key == '.' -> buffer.appendSeparator()
                key == '(' -> buffer.appendLeftParen()
                key == ')' -> buffer.appendRightParen()
                Operator.isOperator(key) -> buffer.appendOperator(Operator.ofSymbolOrNull(key)!!)
                else -> buffer.appendDigit(key)
            }
        }
        return buffer
    }
}
