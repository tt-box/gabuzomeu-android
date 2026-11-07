package eu.ttbox.gabuzomeu.ui.shadok

import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le découpage d'une expression avant affichage : glyphes, séparateurs, et le reste. */
class ExpressionTokenTest {

    @Test
    fun `une expression melangee donne la suite de ses elements`() {
        val tokens = tokenize("◿_×⅃,◯")

        assertEquals(
            listOf(
                ExpressionToken.Glyph(ShadokDigit.MEU),
                ExpressionToken.Glyph(ShadokDigit.BU),
                ExpressionToken.Separator('×'),
                ExpressionToken.Glyph(ShadokDigit.ZO),
                ExpressionToken.Other(','),
                ExpressionToken.Glyph(ShadokDigit.GA),
            ),
            tokens,
        )
    }

    @ParameterizedTest
    @ValueSource(chars = ['+', '−', '×', '÷', '(', ')', '-', '*', '/'])
    fun `les operateurs et les parentheses sont des separateurs`(character: Char) {
        assertEquals(listOf(ExpressionToken.Separator(character)), tokenize(character.toString()))
    }

    @Test
    fun `les caracteres inconnus passent tels quels`() {
        assertEquals(
            listOf(
                ExpressionToken.Other('≈'),
                ExpressionToken.Other('7'),
                ExpressionToken.Other(' '),
            ),
            tokenize("≈7 "),
        )
    }

    @Test
    fun `une expression vide ne donne rien`() {
        assertTrue(tokenize("").isEmpty())
    }

    @Test
    fun `les quatre glyphes sont reconnus`() {
        val digits = tokenize(ShadokDigit.entries.joinToString("") { it.glyph.toString() })

        assertEquals(ShadokDigit.entries.map(ExpressionToken::Glyph), digits)
    }
}
