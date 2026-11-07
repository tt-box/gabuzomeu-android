package eu.ttbox.gabuzomeu.ui.calculator.components

import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.ui.KeypadTags
import eu.ttbox.gabuzomeu.ui.calculator.KeyAction
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les six dispositions du pavé : un croisement mode de calcul x notation chacune. */
class KeypadLayoutTest {

    private fun actions(mode: CalculationMode, notation: NumberNotation) =
        KeypadLayout.forMode(mode, notation).flatten().map { it.action }

    private val allPairs = CalculationMode.entries.flatMap { mode ->
        NumberNotation.entries.map { mode to it }
    }

    private fun digitsOf(mode: CalculationMode, notation: NumberNotation) =
        actions(mode, notation).filterIsInstance<KeyAction.Digit>().map { it.character }

    @Test
    fun `les six croisements ont chacun un pave sans rangee vide`() {
        assertEquals(6, allPairs.size)
        allPairs.forEach { (mode, notation) ->
            val rows = KeypadLayout.forMode(mode, notation)
            assertTrue(rows.isNotEmpty(), "$mode $notation")
            assertTrue(rows.all { it.isNotEmpty() }, "$mode $notation")
        }
    }

    @Test
    fun `une rangee ne depasse jamais quatre unites de largeur`() {
        allPairs.forEach { (mode, notation) ->
            KeypadLayout.forMode(mode, notation).forEach { row ->
                assertTrue(row.sumOf { it.weight.toDouble() } <= 4.0, "$mode $notation")
            }
        }
    }

    @Test
    fun `le pave decimal offre les dix chiffres et aucun glyphe Shadok`() {
        CalculationMode.entries.forEach { mode ->
            val keys = KeypadLayout.forMode(mode, NumberNotation.DECIMAL).flatten()
            assertTrue(keys.none { it.glyph != null }, "$mode")
            assertEquals(
                ('0'..'9').toSet(),
                digitsOf(mode, NumberNotation.DECIMAL).toSet(),
                "$mode",
            )
        }
    }

    @Test
    fun `le pave Shadok offre exactement les quatre chiffres Shadok`() {
        CalculationMode.entries.forEach { mode ->
            val digits = digitsOf(mode, NumberNotation.SHADOK)
            assertEquals(ShadokDigit.entries.map { it.glyph }.toSet(), digits.toSet(), "$mode")
            assertEquals(4, digits.size, "$mode")
        }
    }

    @ParameterizedTest
    @EnumSource(NumberNotation::class)
    fun `la NPI a Enter, Swap et Drop, sans parentheses ni evaluation`(notation: NumberNotation) {
        val actions = actions(CalculationMode.RPN, notation)

        assertTrue(KeyAction.Enter in actions)
        assertTrue(KeyAction.Swap in actions)
        assertTrue(KeyAction.Drop in actions)
        assertTrue(KeyAction.Negate in actions)
        assertFalse(KeyAction.LeftParen in actions)
        assertFalse(KeyAction.RightParen in actions)
        assertFalse(KeyAction.Evaluate in actions)
    }

    @ParameterizedTest
    @EnumSource(NumberNotation::class)
    fun `le mode classique n'a aucune touche de pile`(notation: NumberNotation) {
        val actions = actions(CalculationMode.CLASSIC, notation)

        assertFalse(KeyAction.Enter in actions)
        assertFalse(KeyAction.Swap in actions)
        assertFalse(KeyAction.Drop in actions)
        assertTrue(KeyAction.LeftParen in actions)
        assertTrue(KeyAction.RightParen in actions)
        assertTrue(KeyAction.Evaluate in actions)
    }

    @ParameterizedTest
    @EnumSource(NumberNotation::class)
    fun `le mode simple n'a ni parentheses ni touches de pile`(notation: NumberNotation) {
        val actions = actions(CalculationMode.SIMPLE, notation)

        listOf(
            KeyAction.LeftParen,
            KeyAction.RightParen,
            KeyAction.Enter,
            KeyAction.Swap,
            KeyAction.Drop,
        ).forEach { assertFalse(it in actions, "$it") }
        assertTrue(KeyAction.Evaluate in actions)
    }

    @Test
    fun `les identifiants de test sont uniques dans chaque pave`() {
        allPairs.forEach { (mode, notation) ->
            val tags = actions(mode, notation).map(KeypadTags::of)
            assertEquals(tags.size, tags.toSet().size, "$mode $notation")
        }
    }
}
