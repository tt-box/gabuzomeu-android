package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les cinq règles de saisie du projet d'origine, reprises depuis
 * `CalculatorEditable.internalReplace` (`CalculatorEditable.java:49-97`), désormais
 * sous forme de fonctions pures testables sans émulateur.
 */
class ExpressionBufferTest {

    // ------------------------------------------------------- règle 1 : le point

    @Test
    fun `regle 1 - pas de second separateur dans un meme nombre`() {
        assertEquals("1.5", decimalOf(type("1.5")))
        // Le second point est ignore, pas rejete brutalement.
        assertEquals("1.5", decimalOf(type("1.5.")))
        assertEquals("1.52", decimalOf(type("1.5.2")))
    }

    @Test
    fun `regle 1 - chaque nombre a droit a son propre separateur`() {
        assertEquals("1.5+2.5", decimalOf(type("1.5+2.5")))
    }

    @Test
    fun `un separateur en tete materialise le zero`() {
        assertEquals("0.5", decimalOf(type(".5")))
    }

    // ----------------------------------------------------- règle 2 : deux moins

    @Test
    fun `regle 2 - pas deux moins successifs`() {
        assertEquals("−5", decimalOf(type("--5")))
    }

    // ------------------------------------------- règle 3 : opérateurs successifs

    @Test
    fun `regle 3 - un operateur non-moins ecrase toute la traine d'operateurs`() {
        assertEquals("5×", decimalOf(type("5+*")))
        assertEquals("5÷", decimalOf(type("5+*/")))
        // « 5×− » puis « + » : les DEUX operateurs sont ecrases.
        assertEquals("5+", decimalOf(type("5*-+")))
    }

    @Test
    fun `regle 3 - un moins n'ecrase qu'un plus`() {
        assertEquals("5−", decimalOf(type("5+-")))
        assertEquals("5−3", decimalOf(type("5+-3")))
    }

    @Test
    fun `regle 3 - un moins apres fois ou divise devient un moins unaire`() {
        // La condition « text != MINUS || prevChar == '+' » de CalculatorEditable
        // n'ecrase pas ici : le moins s'ajoute.
        assertEquals("5×−", decimalOf(type("5*-")))
        assertEquals("2×−3", decimalOf(type("2*-3")))
        assertEquals("8÷−2", decimalOf(type("8/-2")))
    }

    @Test
    fun `ecraser un operateur ne peut pas laisser un operateur en tete`() {
        // « − » puis « + » : ecraser ramenerait « + » en tete, ce que la regle 4
        // interdit — la saisie est donc ignoree.
        assertEquals("−", decimalOf(type("-+")))
        assertEquals("(−", decimalOf(type("(-+")))
    }

    // ------------------------------------------ règle 4 : opérateur en tête

    @Test
    fun `regle 4 - pas d'operateur en tete, sauf le moins`() {
        assertEquals("", decimalOf(type("+")))
        assertEquals("", decimalOf(type("*")))
        assertEquals("", decimalOf(type("/")))
        assertEquals("−", decimalOf(type("-")))
        assertEquals("−5", decimalOf(type("-5")))
    }

    @Test
    fun `regle 4 - s'applique aussi juste apres une parenthese ouvrante`() {
        assertEquals("(−5", decimalOf(type("(-5")))
        assertEquals("(5", decimalOf(type("(*5")))
    }

    // ------------------------------------------------------------ parenthèses

    @Test
    fun `pas de multiplication implicite`() {
        // « 2( » et « )3 » sont refuses : la calculatrice n'invente pas d'operateur.
        assertEquals("2", decimalOf(type("2(")))
        assertEquals("(2)", decimalOf(type("(2)3")))
    }

    @Test
    fun `une fermante sans ouvrante est ignoree`() {
        assertEquals("5", decimalOf(type("5)")))
    }

    @Test
    fun `une fermante juste apres une ouvrante est ignoree`() {
        assertEquals("(", decimalOf(type("()")))
    }

    // ------------------------------------------------------ suppression, remise à zéro

    @Test
    fun `la suppression retire un chiffre a la fois puis l'atome`() {
        val buffer = type("12+34")
        assertEquals("12+3", decimalOf(buffer.deleteLast()))
        assertEquals("12+", decimalOf(buffer.deleteLast().deleteLast()))
        assertEquals("12", decimalOf(buffer.deleteLast().deleteLast().deleteLast()))
    }

    @Test
    fun `supprimer sur un tampon vide ne fait rien`() {
        assertTrue(ExpressionBuffer().deleteLast().isEmpty)
    }

    @Test
    fun `clear vide l'expression mais conserve le mode de saisie`() {
        val cleared = type("12+34", NumberNotation.DECIMAL).clear()
        assertTrue(cleared.isEmpty)
        assertEquals(NumberNotation.DECIMAL, cleared.notation)
    }

    // --------------------------------------------------- saisie en mode Shadok

    @Test
    fun `la saisie Shadok se projette exactement en decimal`() {
        // ⅃ = Zo = 2, donc « ⅃⅃ » = 22 en base 4 = 10 en decimal.
        val buffer = type("⅃⅃", NumberNotation.SHADOK)

        assertEquals("10", decimalOf(buffer))
        assertEquals("⅃⅃", buffer.render(ExpressionDisplay.SHADOK_GLYPHS).text)
        assertEquals("ZoZo", buffer.render(ExpressionDisplay.SHADOK_LABELS).text)
    }

    @Test
    fun `un chiffre decimal est refuse en mode Shadok et inversement`() {
        assertTrue(type("7", NumberNotation.SHADOK).isEmpty)
        assertTrue(type("◿", NumberNotation.DECIMAL).isEmpty)
    }

    @Test
    fun `les operateurs restent saisissables dans les deux modes`() {
        val buffer = type("◿×⅃", NumberNotation.SHADOK)
        assertEquals("Meu×Zo", buffer.render(ExpressionDisplay.SHADOK_LABELS).text)
        assertEquals("3×2", decimalOf(buffer))
    }

    // ------------------------------------------------- projection vers le Shadok

    @Test
    fun `6 en decimal se projette en BuZo`() {
        val buffer = type("6")

        val labels = buffer.render(ExpressionDisplay.SHADOK_LABELS)
        assertEquals("BuZo", labels.text)
        assertFalse(labels.approximate)
        assertEquals("_⅃", buffer.render(ExpressionDisplay.SHADOK_GLYPHS).text)
    }

    @Test
    fun `la base 4 ecrit les memes chiffres que les glyphes et les noms`() {
        // La quatrieme ecriture existe pour le presse-papiers : « 12 » se colle et se lit
        // partout, la ou « _⅃ » depend d'une police qui porte U+2143.
        val buffer = type("6")

        assertEquals("12", buffer.render(ExpressionDisplay.SHADOK_BASE4).text)
        assertEquals("_⅃", buffer.render(ExpressionDisplay.SHADOK_GLYPHS).text)
        assertEquals("BuZo", buffer.render(ExpressionDisplay.SHADOK_LABELS).text)
    }

    @Test
    fun `la base 4 rend la frappe Shadok verbatim, comme les deux autres`() {
        // Le cas que la lecture glyphe par glyphe doit couvrir : une saisie en cours, dont
        // les zeros de tete et le separateur final ne doivent pas etre normalises.
        val buffer = type("◯⅃.", NumberNotation.SHADOK)

        assertEquals("02.", buffer.render(ExpressionDisplay.SHADOK_BASE4).text)
        assertEquals("◯⅃.", buffer.render(ExpressionDisplay.SHADOK_GLYPHS).text)
        assertEquals("GaZo.", buffer.render(ExpressionDisplay.SHADOK_LABELS).text)
    }

    @Test
    fun `les trois ecritures Shadok sont approchees ensemble`() {
        // Elles decrivent les memes chiffres : l'une ne peut pas etre exacte quand l'autre
        // est tronquee, sinon le marqueur « ≈ » mentirait selon le reglage choisi.
        val buffer = type("0.1")

        assertTrue(buffer.render(ExpressionDisplay.SHADOK_GLYPHS).approximate)
        assertTrue(buffer.render(ExpressionDisplay.SHADOK_LABELS).approximate)
        assertTrue(buffer.render(ExpressionDisplay.SHADOK_BASE4).approximate)
    }

    @Test
    fun `une decimale non representable en base 4 est signalee comme approchee`() {
        // 0.1 decimal = 0.0121212... en base 4 : developpement periodique.
        val approx = type("0.1").render(ExpressionDisplay.SHADOK_LABELS)
        assertTrue(approx.approximate, "0.1 n'a pas de developpement fini en base 4")

        // 0.25 en revanche est exact : 0.1 en base 4.
        val exact = type("0.25").render(ExpressionDisplay.SHADOK_LABELS)
        assertFalse(exact.approximate)
        assertEquals("Ga.Bu", exact.text)
    }

    @Test
    fun `la projection vers le decimal n'est jamais approchee`() {
        // Toute valeur finie en base 4 est finie en base 10, puisque 4 = 2^2.
        listOf("◯.⅃", "◿◿.◿◿◿", "_⅃◯").forEach { keys ->
            assertFalse(
                type(keys, NumberNotation.SHADOK).render(ExpressionDisplay.DECIMAL).approximate,
                "la projection decimale de $keys devrait etre exacte",
            )
        }
    }

    // ------------------------------------------------------ changement de mode

    @Test
    fun `changer de mode convertit toute l'expression d'un bloc`() {
        val decimal = type("6+2")
        val shadok = decimal.withNotation(NumberNotation.SHADOK)

        assertEquals(NumberNotation.SHADOK, shadok.notation)
        assertEquals("_⅃+⅃", shadok.render(ExpressionDisplay.SHADOK_GLYPHS).text)
        // La valeur est preservee.
        assertEquals("6+2", decimalOf(shadok))
    }

    @Test
    fun `changer de mode et revenir preserve la valeur`() {
        val original = type("6+2.5")
        val roundTripped = original
            .withNotation(NumberNotation.SHADOK)
            .withNotation(NumberNotation.DECIMAL)

        assertEquals("6+2.5", decimalOf(roundTripped))
    }

    @Test
    fun `changer pour le meme mode ne fait rien`() {
        val buffer = type("6")
        assertEquals(buffer, buffer.withNotation(NumberNotation.DECIMAL))
    }

    // ------------------------------------------------- bascule sans perte de valeur

    @Test
    fun `basculer de notation ne change jamais la valeur`() {
        // 0.1 n'a pas d'ecriture finie en base 4 : l'aller-retour passait par 16 chiffres
        // tronques et revenait en 0.0999999998...
        val roundTrip = type("0.1")
            .withNotation(NumberNotation.SHADOK)
            .withNotation(NumberNotation.DECIMAL)

        assertEquals("0.1", decimalOf(roundTrip))
        assertEquals(EvalResult.Success(Rational.ofDecimal("0.1")), Evaluator.evaluate(roundTrip))
    }

    @Test
    fun `en Shadok la valeur exacte est conservee et l'ecriture signalee approchee`() {
        val inShadok = type("0.1+1").withNotation(NumberNotation.SHADOK)

        assertTrue(inShadok.render(ExpressionDisplay.SHADOK_GLYPHS).approximate)
        assertFalse(inShadok.render(ExpressionDisplay.DECIMAL).approximate)
        assertEquals("0.1+1", decimalOf(inShadok))
        assertEquals(EvalResult.Success(Rational.ofDecimal("1.1")), Evaluator.evaluate(inShadok))
    }

    @Test
    fun `un nombre exact sans ecriture decimale finie est signale approche en decimal`() {
        val third = Rational.of(1) / Rational.of(3)
        val buffer = ExpressionBuffer(
            atoms = listOf(
                Atom.Number(NumberNotation.DECIMAL, third.toDecimalString(), exact = third),
            ),
        )

        assertTrue(buffer.render(ExpressionDisplay.DECIMAL).approximate)
        val timesThree = buffer.appendOperator(Operator.TIMES).appendDigit('3')
        assertEquals(EvalResult.Success(Rational.of(1)), Evaluator.evaluate(timesThree))
    }

    @Test
    fun `retoucher un nombre exact repart de ses chiffres`() {
        val third = Rational.of(1) / Rational.of(3)
        val buffer = ExpressionBuffer(
            atoms = listOf(Atom.Number(NumberNotation.DECIMAL, "0.33", exact = third)),
        )

        assertEquals(
            EvalResult.Success(Rational.ofDecimal("0.3")),
            Evaluator.evaluate(buffer.deleteLast()),
        )
        assertEquals(
            EvalResult.Success(Rational.ofDecimal("0.331")),
            Evaluator.evaluate(buffer.appendDigit('1')),
        )
    }

    // ------------------------------------------------ collage d'une valeur exacte

    @Test
    fun `un nombre colle avec sa valeur exacte la garde en rebasculant`() {
        val seven = Rational.ofDecimal("0.7")
        val glyphs = Atom.Number(NumberNotation.DECIMAL, "0.7").toBase4()
            .let { ShadokFormatter.format(it, ShadokNotation.GLYPHS, markApproximation = false) }

        val pasted = ExpressionBuffer(NumberNotation.SHADOK).appendNumber(glyphs, seven)

        assertTrue(pasted.render(ExpressionDisplay.SHADOK_GLYPHS).approximate)
        assertEquals(EvalResult.Success(seven), Evaluator.evaluate(pasted))
        assertEquals("0.7", decimalOf(pasted.withNotation(NumberNotation.DECIMAL)))
    }

    @Test
    fun `un nombre colle avec sa valeur exacte remplace la frappe en cours`() {
        val third = Rational.of(1) / Rational.of(3)

        val pasted = type("2+5").appendNumber("0.33", third)

        assertEquals("2+0.33", decimalOf(pasted))
        assertEquals(
            EvalResult.Success(Rational.of(7) / Rational.of(3)),
            Evaluator.evaluate(pasted),
        )
    }

    @Test
    fun `un nombre colle sans valeur exacte se tape comme au clavier`() {
        assertEquals("57", decimalOf(type("5").appendNumber("7")))
    }

    // ------------------------------------------------ persistance des valeurs exactes

    @Test
    fun `les valeurs exactes survivent a la persistance`() {
        val third = Rational.of(1) / Rational.of(3)
        val buffer = type("2+").appendNumber(third.toDecimalString(), third)

        assertEquals(";1/3", buffer.exactKeys())
        val restored = ExpressionBuffer.replay(
            buffer.replayKeys(),
            buffer.notation,
            buffer.exactKeys(),
        )

        assertEquals(buffer, restored)
        assertEquals(
            EvalResult.Success(Rational.of(7) / Rational.of(3)),
            Evaluator.evaluate(restored),
        )
    }

    @Test
    fun `sans valeur exacte la cle reste vide comme dans l'ancien format`() {
        val typed = type("2+3")

        assertEquals("", typed.exactKeys())
        assertEquals(typed, ExpressionBuffer.replay("2+3"))
    }

    @Test
    fun `une valeur exacte invalide est ignoree`() {
        val digitsOnly = ExpressionBuffer.replay("0.33")

        // Illisible, dénominateur nul, négative, ou mal alignée sur les nombres rejoués.
        listOf("abc", "1/0", "-1/3", "1/3;1/3").forEach { stored ->
            assertEquals(digitsOnly, ExpressionBuffer.replay("0.33", exactKeys = stored), stored)
        }
    }

    // -------------------------------------------------------------- utilitaires

    private fun decimalOf(buffer: ExpressionBuffer): String =
        buffer.render(ExpressionDisplay.DECIMAL).text

    private fun type(
        keys: String,
        notation: NumberNotation = NumberNotation.DECIMAL,
    ): ExpressionBuffer {
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
