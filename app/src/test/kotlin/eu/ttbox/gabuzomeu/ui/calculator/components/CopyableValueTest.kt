package eu.ttbox.gabuzomeu.ui.calculator.components

import eu.ttbox.gabuzomeu.ui.calculator.CalculatorUiState
import eu.ttbox.gabuzomeu.ui.calculator.StackLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ce qu'on peut copier d'une valeur affichée. */
class CopyableValueTest {

    private val value = CopyableValue(glyphs = "G", labels = "L", base4 = "B", decimal = "D")

    @Test
    fun `chaque ecriture copiable donne son texte`() {
        assertEquals("G", value.textFor(ValueWriting.GLYPHS))
        assertEquals("L", value.textFor(ValueWriting.LABELS))
        assertEquals("D", value.textFor(ValueWriting.DECIMAL))
    }

    @Test
    fun `une valeur sans decimal ni glyphes est vide meme avec des libelles`() {
        assertTrue(CopyableValue(glyphs = "", labels = "Zo", base4 = "", decimal = "").isEmpty)
        assertFalse(value.isEmpty)
    }

    @Test
    fun `l'etat de l'afficheur se copie champ pour champ`() {
        val state = CalculatorUiState(glyphs = "g", labels = "l", base4 = "b", decimal = "d")

        assertEquals(CopyableValue("g", "l", "b", "d"), CopyableValue.of(state))
    }

    @Test
    fun `un niveau de pile se copie champ pour champ`() {
        val level = StackLevel(glyphs = "g", labels = "l", decimal = "d", base4 = "b")

        assertEquals(CopyableValue("g", "l", "b", "d"), CopyableValue.of(level))
    }
}
