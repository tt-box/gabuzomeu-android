package eu.ttbox.gabuzomeu.ui.calculator.components

import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.ui.calculator.StackLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Le décimal d'un niveau de pile : précédé de `≈` seulement s'il est tronqué. */
class StackLevelMarkerTest {

    @Test
    fun `un niveau exact garde son decimal tel quel`() {
        val level = StackLevel(glyphs = "_", labels = "Bu", decimal = "1")

        assertEquals("1", level.decimalWithMarker())
    }

    @Test
    fun `un niveau tronque est precede du signe d'approximation`() {
        val level =
            StackLevel(glyphs = "_", labels = "Bu", decimal = "0.333", decimalApproximate = true)

        assertEquals("${ShadokFormatter.APPROXIMATION}0.333", level.decimalWithMarker())
    }

    @Test
    fun `l'approximation du Shadok n'ajoute rien au decimal`() {
        val level = StackLevel(glyphs = "_", labels = "Bu", decimal = "1", shadokApproximate = true)

        assertEquals("1", level.decimalWithMarker())
    }
}
