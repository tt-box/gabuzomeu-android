package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La lecture du presse-papiers.
 *
 * C'est le pendant de la copie, et le seul endroit du trajet qui contienne une décision :
 * l'application écrit une valeur dans quatre écritures, elle doit toutes les relire, et
 * arbitrer le cas où deux d'entre elles se ressemblent. Testable sur la JVM parce que la
 * lecture d'un texte n'a rien d'une affaire de plateforme — seul le presse-papiers en est une.
 */
class ClipboardNumberTest {

    /** La frappe rejouée à travers les vraies règles de saisie, puis relue en décimal. */
    private fun typed(pasted: PastedNumber, notation: NumberNotation): String =
        ExpressionBuffer.replay(pasted.keys, notation).render(ExpressionDisplay.DECIMAL).text

    @ParameterizedTest(name = "\"{0}\" en decimal -> {1}")
    @CsvSource(
        // Les quatre écritures d'un même nombre mènent toutes à la même frappe.
        "_⅃,     6",
        "BuZo,   6",
        "buzo,   6",
        "6,      6",
        // Le décimal reste du décimal : c'est le pavé actif qui décide, et il est en décimal.
        "12,     12",
        "42,     42",
        // Les espaces d'un copier-coller maladroit ne doivent pas faire échouer la lecture.
        "'  6  ', 6",
    )
    fun `un nombre se lit dans ses quatre ecritures`(text: String, expected: String) {
        val pasted = assertNotNull(ClipboardNumber.parseOrNull(text, NumberNotation.DECIMAL), text)

        assertEquals(expected, typed(pasted, NumberNotation.DECIMAL))
        assertFalse(pasted.negative)
    }

    @Test
    fun `douze vaut douze en decimal et six en Shadok`() {
        // LE seul cas ambigu : « 12 » est un nombre valide dans les deux bases. Le pavé actif
        // tranche, parce que c'est là que le collage arrive. C'est aussi ce qui rend fidèle
        // l'aller-retour « copier la base 4, puis coller » quand on est en Shadok.
        val inDecimal = assertNotNull(ClipboardNumber.parseOrNull("12", NumberNotation.DECIMAL))
        val inShadok = assertNotNull(ClipboardNumber.parseOrNull("12", NumberNotation.SHADOK))

        assertEquals("12", typed(inDecimal, NumberNotation.DECIMAL))
        assertEquals("6", typed(inShadok, NumberNotation.SHADOK))
    }

    @Test
    fun `un nombre hors base 4 reste decimal, meme sur le pave Shadok`() {
        // « 42 » ne s'écrit pas avec des chiffres de base 4 : le lire en base 4 serait
        // impossible, donc c'est du décimal — et le pavé Shadok le tapera en glyphes.
        val pasted = assertNotNull(ClipboardNumber.parseOrNull("42", NumberNotation.SHADOK))

        assertEquals("42", typed(pasted, NumberNotation.SHADOK))
        // 42 = 222 en base 4, soit trois Zo.
        assertEquals("⅃⅃⅃", pasted.keys)
    }

    @Test
    fun `un collage tronque est signale et garde sa valeur exacte`() {
        val inShadok = assertNotNull(ClipboardNumber.parseOrNull("0.7", NumberNotation.SHADOK))
        assertTrue(inShadok.approximate, "0.7 n'a pas d'ecriture finie en base 4")
        assertEquals(Rational.ofDecimal("0.7"), inShadok.value)

        val exact = assertNotNull(ClipboardNumber.parseOrNull("−0.5", NumberNotation.SHADOK))
        assertFalse(exact.approximate)
        assertEquals(Rational.ofDecimal("-0.5"), exact.value)
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["−_⅃", "-_⅃", "−BuZo", "-6", "−6"])
    fun `le signe est porte a part, jamais dans les touches`(text: String) {
        // `Atom.Number.digits` ne contient pas de signe, et en infixe un négatif est un
        // opérateur : le mélanger aux chiffres produirait une frappe intapable.
        val pasted = assertNotNull(ClipboardNumber.parseOrNull(text, NumberNotation.DECIMAL))

        assertTrue(pasted.negative, text)
        assertEquals("6", pasted.keys)
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "Ga.Zo,   0.5",
        "◯.⅃,     0.5",
        "0.5,     0.5",
        "_⅃.◿,    6.75",
        "0.25,    0.25",
        // Un séparateur en tête ou en queue est un état de frappe légitime.
        ".5,      0.5",
        "5.,      5",
    )
    fun `les fractions traversent la lecture`(text: String, expected: String) {
        val pasted = assertNotNull(ClipboardNumber.parseOrNull(text, NumberNotation.DECIMAL), text)

        assertEquals(expected, typed(pasted, NumberNotation.DECIMAL))
    }

    @ParameterizedTest
    @EnumSource(NumberNotation::class)
    fun `les touches rendues sont toujours tapables`(notation: NumberNotation) {
        // La propriété qui compte vraiment : `keys` ne contient que ce que `appendDigit`
        // accepte. Sinon le collage produirait un tampon vide sans rien dire.
        listOf("6", "_⅃", "BuZo", "0.25", "−42", "255").forEach { text ->
            val pasted = assertNotNull(ClipboardNumber.parseOrNull(text, notation), text)

            pasted.keys.forEach { key ->
                val tapable = when (notation) {
                    NumberNotation.DECIMAL -> key in '0'..'9'
                    NumberNotation.SHADOK -> ShadokDigit.isGlyph(key)
                }
                assertTrue(tapable || key == '.', "\"$text\" -> touche \"$key\" intapable")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpressionDisplay::class)
    fun `chaque ecriture copiee se recolle a l'identique`(display: ExpressionDisplay) {
        // L'aller-retour complet : ce que l'application copie, elle sait le relire. La base 4
        // n'est fidèle qu'en notation Shadok — c'est la règle d'ambiguïté, et elle est ici
        // vérifiée plutôt que décrite.
        val notation = if (display == ExpressionDisplay.SHADOK_BASE4) {
            NumberNotation.SHADOK
        } else {
            NumberNotation.DECIMAL
        }
        val copied = ExpressionBuffer.replay("6", NumberNotation.DECIMAL).render(display).text

        val pasted = assertNotNull(ClipboardNumber.parseOrNull(copied, notation), copied)

        assertEquals("6", typed(pasted, notation))
    }

    @Test
    fun `la regle d'ambiguite vaut aussi pour les fractions`() {
        // Conséquence à connaître : « 0.1 » est une écriture base 4 valide, donc sur le pavé
        // Shadok il vaut **un quart**, pas un dixième. Même règle que « 12 », appliquée après
        // le séparateur — et c'est bien ce qu'attend qui vient de copier la base 4.
        val inShadok = assertNotNull(ClipboardNumber.parseOrNull("0.1", NumberNotation.SHADOK))
        val inDecimal = assertNotNull(ClipboardNumber.parseOrNull("0.1", NumberNotation.DECIMAL))

        assertEquals("0.25", typed(inShadok, NumberNotation.SHADOK))
        assertEquals("0.1", typed(inDecimal, NumberNotation.DECIMAL))
    }

    @Test
    fun `une valeur sans ecriture finie en base 4 est tronquee, pas refusee`() {
        // 0.7 décimal — un 7 n'existe pas en base 4, donc pas d'ambiguïté ici — donne
        // 0.2303030… en base 4. Le « ≈ » n'étant pas une touche, la frappe est tronquée :
        // exactement ce que fait déjà `withNotation` quand on bascule le pavé en cours de saisie.
        val pasted = assertNotNull(ClipboardNumber.parseOrNull("0.7", NumberNotation.SHADOK))

        assertTrue(pasted.keys.startsWith("◯.⅃◿◯◿"), pasted.keys)
        assertTrue(pasted.keys.none { it == '≈' })
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(
        strings = [
            // Rien à lire.
            "", " ", "−", "-", ".", "−.",
            // Du texte, ou une écriture mal formée.
            "abc", "BuXo", "Meuu", "6a", "a6",
            // Une fraction n'est pas une frappe : le pavé n'a pas de barre de division.
            "1/3", "−1/3",
            // Deux séparateurs, ou un signe hors de la tête.
            "1.2.3", "6−", "6−2", "1+2",
            // Séparateurs de milliers et notations exotiques : refusés à dessein. « 1e9 »
            // serait accepté par BigDecimal, et « 1e999999999 » épuiserait la mémoire
            // sur un simple collage.
            "1 000", "1,000", "1e9", "1E9", "0x10", "+6", "6f", "∞",
        ],
    )
    fun `ce qui n'est pas un nombre rend null`(text: String) {
        NumberNotation.entries.forEach { notation ->
            assertNull(
                ClipboardNumber.parseOrNull(text, notation),
                "\"$text\" ne devrait pas se coller en $notation",
            )
        }
    }

    @Test
    fun `un nombre tres long ne fait pas exploser la lecture`() {
        // Un collage arbitraire peut être énorme. Ce qui doit être exclu, c'est la mise à
        // l'échelle exponentielle ; une longue suite de chiffres reste linéaire et acceptable.
        val long = "9".repeat(200)

        val pasted = assertNotNull(ClipboardNumber.parseOrNull(long, NumberNotation.DECIMAL))

        assertEquals(long, pasted.keys)
    }
}
