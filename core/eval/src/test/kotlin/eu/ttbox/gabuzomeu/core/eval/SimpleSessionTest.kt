package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La calculatrice a execution immediate — celle de la machine a calculer des Shadoks.
 *
 * Le premier test est celui qui compte : c'est la regle qui distingue ce moteur de celui du
 * mode classique, et rien d'autre ne le dit.
 */
class SimpleSessionTest {

    // ------------------------------------------------------------ la regle du mode

    @Test
    fun `les operations se resolvent au fil de la frappe, sans priorite`() {
        // 1 + 2 x 3 : chaque operateur resout le precedent, donc (1+2) puis x3.
        // Le mode classique, qui parse une expression entiere, donnerait 7.
        val session = type("1").pressing(Operator.PLUS)
            .type("2").pressing(Operator.TIMES)
            .type("3").equalling()

        assertEquals(Rational.of(9), session.accumulator)
        assertEquals("9", session.valueText())
    }

    @Test
    fun `l'operateur affiche le resultat intermediaire`() {
        // C'est ce qui rend la machine lisible : on voit 3 des l'appui sur x, sans attendre =.
        val session = type("1").pressing(Operator.PLUS).type("2").pressing(Operator.TIMES)

        assertEquals("3", session.valueText())
        assertFalse(session.entering, "la frappe a ete consommee par l'operateur")
    }

    @Test
    fun `l'ordre des operandes est respecte`() {
        assertEquals(Rational.of(7), type("10").pressing(Operator.MINUS).type("3").result())
        assertEquals(Rational.of(4), type("12").pressing(Operator.DIVIDE).type("3").result())
    }

    @Test
    fun `deux operateurs de suite remplacent le premier sans recalculer`() {
        // Le geste de correction : un + malencontreux se rattrape par un x, sans effacer.
        val session = type("6").pressing(Operator.PLUS).pressing(Operator.TIMES)
            .type("7").equalling()

        assertEquals(Rational.of(42), session.accumulator)
    }

    // ------------------------------------------------------------------ le signe egal

    @Test
    fun `egal sans operation en attente fige simplement la frappe`() {
        val session = type("6").equalling()

        assertEquals(Rational.of(6), session.accumulator)
        assertTrue(session.showingResult)
    }

    @Test
    fun `un second egal ne repete pas la derniere operation`() {
        // Choix assume : repeter demanderait de memoriser l'operande droit, un etat
        // invisible de plus dans un mode qui s'appelle Simple.
        val once = type("2").pressing(Operator.PLUS).type("3").equalling()

        assertEquals(Rational.of(5), once.equalling().accumulator)
    }

    @Test
    fun `un chiffre apres un resultat repart de zero`() {
        val session = type("2").pressing(Operator.PLUS).type("3").equalling().type("7")

        assertEquals("7", session.valueText())
        assertNull(session.accumulator, "le 5 precedent n'est plus la")
        assertNull(session.pending)
    }

    @Test
    fun `un operateur apres un resultat prolonge le calcul`() {
        // La symetrie de la regle precedente : on enchaine sur ce qu'on vient d'obtenir.
        val session = type("2").pressing(Operator.PLUS).type("3").equalling()
            .pressing(Operator.TIMES).type("4").equalling()

        assertEquals(Rational.of(20), session.accumulator)
    }

    // --------------------------------------------------------------------- correction

    @Test
    fun `la correction n'entame jamais l'accumulateur`() {
        // ⌫ corrige ce qu'on tape ; il ne defait pas un calcul.
        val session = type("6").pressing(Operator.PLUS).type("77").deleteLast().deleteLast()

        assertEquals(Rational.of(6), session.accumulator)
        assertEquals(Operator.PLUS, session.pending)
        assertFalse(session.entering)
    }

    @Test
    fun `pomper efface tout`() {
        val session = type("6").pressing(Operator.PLUS).type("7").clear()

        assertTrue(session.isPristine)
        assertEquals("", session.valueText())
    }

    // --------------------------------------------------------------------- les erreurs

    @Test
    fun `une division par zero laisse la session intacte`() {
        val before = type("6").pressing(Operator.DIVIDE).type("0")

        val outcome = before.evaluate()

        assertEquals(EvalError.DIVISION_BY_ZERO, outcome.error)
        assertEquals(before, outcome.session, "rien ne doit etre perdu par une erreur")
    }

    @Test
    fun `une division par zero declenchee par un operateur laisse aussi tout en place`() {
        val before = type("6").pressing(Operator.DIVIDE).type("0")

        val outcome = before.operator(Operator.PLUS)

        assertEquals(EvalError.DIVISION_BY_ZERO, outcome.error)
        assertEquals(before, outcome.session)
    }

    // ----------------------------------------------------------------- exactitude

    @Test
    fun `un tiers reste un tiers`() {
        val session = type("1").pressing(Operator.DIVIDE).type("3").equalling()

        assertEquals(Rational.ONE, session.accumulator!! * Rational.of(3))
        // Et les deux ecritures se declarent approchees, chacune pour sa raison.
        assertTrue(session.renderValue(ExpressionDisplay.DECIMAL).approximate)
        assertTrue(session.renderValue(ExpressionDisplay.SHADOK_GLYPHS).approximate)
    }

    @Test
    fun `un tiers repris dans un calcul reste exact`() {
        val session = type("1").pressing(Operator.DIVIDE).type("3").equalling()
            .pressing(Operator.TIMES).type("3").equalling()

        assertEquals(Rational.ONE, session.accumulator)
        assertFalse(session.renderValue(ExpressionDisplay.DECIMAL).approximate)
    }

    @Test
    fun `une division peut produire une fraction sans touche virgule`() {
        // Le pave Simple n'a pas de separateur : c'est la division qui amene les fractions.
        val session = type("1").pressing(Operator.DIVIDE).type("2").equalling()

        assertEquals("0.5", session.valueText())
        assertEquals("Ga.Zo", session.text(ExpressionDisplay.SHADOK_LABELS))
    }

    // -------------------------------------------------------------------- l'afficheur

    @Test
    fun `la valeur affichee est la frappe tant qu'elle est en cours`() {
        val session = type("6").pressing(Operator.PLUS).type("7")

        assertEquals("7", session.valueText())
        assertTrue(session.entering)
    }

    @Test
    fun `la valeur affichee est l'accumulateur hors frappe`() {
        val session = type("6").pressing(Operator.PLUS).type("7").equalling()

        assertEquals("13", session.valueText())
        assertFalse(session.entering)
    }

    @Test
    fun `une session vierge n'affiche rien`() {
        assertEquals("", SimpleSession().valueText())
        assertTrue(SimpleSession().isPristine)
    }

    // ---------------------------------------------------------------------- notation

    @Test
    fun `en Shadok seuls les glyphes sont acceptes`() {
        val session = SimpleSession.of(NumberNotation.SHADOK).appendDigit('7')

        assertFalse(session.entering, "un 7 decimal n'existe pas en base 4")
    }

    @Test
    fun `MEU plus ZO donne BuBu`() {
        val session = SimpleSession.of(NumberNotation.SHADOK)
            .appendDigit(ShadokDigit.MEU.glyph)
            .pressing(Operator.PLUS)
            .appendDigit(ShadokDigit.ZO.glyph)
            .equalling()

        // 3 + 2 = 5, et 5 en base 4 s'ecrit 11, soit BuBu.
        assertEquals(Rational.of(5), session.accumulator)
        assertEquals("BuBu", session.text(ExpressionDisplay.SHADOK_LABELS))
    }

    @Test
    fun `changer de notation convertit la frappe et laisse l'accumulateur intact`() {
        val session = type("2").pressing(Operator.PLUS).type("6")
            .withNotation(NumberNotation.SHADOK)

        assertEquals(NumberNotation.SHADOK, session.notation)
        // 6 en base 4 = 12, soit les glyphes Bu Zo.
        assertEquals("_⅃", session.text(ExpressionDisplay.SHADOK_GLYPHS))
        assertEquals(Rational.of(2), session.accumulator)
        assertEquals(Operator.PLUS, session.pending)
    }

    // ------------------------------------------------------------------- persistance

    @Test
    fun `l'aller-retour de persistance reconstruit la session a l'identique`() {
        val session = type("1").pressing(Operator.DIVIDE).type("3").equalling()
            .pressing(Operator.TIMES).type("42")

        assertEquals(session, session.persisted())
    }

    @Test
    fun `l'accumulateur se persiste en fraction, donc sans arrondi`() {
        val session = type("1").pressing(Operator.DIVIDE).type("3").equalling()

        assertEquals("1/3", session.accumulatorKeys())
        assertEquals(session.accumulator, session.persisted().accumulator)
    }

    @Test
    fun `une session vide se persiste et se relit`() {
        val restored = SimpleSession.restore(
            entryKeys = "",
            accumulatorKeys = "",
            pendingKeys = "",
            entryNegative = false,
            notation = NumberNotation.DECIMAL,
        )

        assertTrue(restored.isPristine)
    }

    @Test
    fun `des donnees stockees corrompues ne peuvent pas violer les invariants`() {
        // « 1+2 » n'est pas une frappe valide, « ? » n'est pas un operateur, « x » n'est pas
        // une fraction : chacun est ignore plutot que fatal.
        val restored = SimpleSession.restore(
            entryKeys = "1+2",
            accumulatorKeys = "x",
            pendingKeys = "?",
            entryNegative = false,
            notation = NumberNotation.DECIMAL,
        )

        assertEquals(1, restored.entry.atoms.size)
        assertEquals("1", restored.valueText())
        assertNull(restored.accumulator)
        assertNull(restored.pending)
    }

    @Test
    fun `un signe stocke sans chiffres est ignore`() {
        val restored = SimpleSession.restore(
            entryKeys = "",
            accumulatorKeys = "5",
            pendingKeys = "+",
            entryNegative = true,
            notation = NumberNotation.DECIMAL,
        )

        assertFalse(restored.entryNegative, "un signe invisible resurgirait au chiffre suivant")
    }

    // ------------------------------------------------------------------------- signe

    @Test
    fun `un nombre negatif garde son signe dans le calcul`() {
        // Le pave n'a pas de ± ; ce chemin sert aux nombres colles.
        val session = type("2").pressing(Operator.PLUS).type("6").negate()

        assertEquals("−6", session.valueText(), "le signe se voit pendant la frappe")
        assertEquals(Rational.of(-4), session.equalling().accumulator)
    }

    @Test
    fun `effacer le dernier chiffre efface aussi le signe`() {
        val session = type("6").negate().deleteLast()

        assertFalse(session.entryNegative)
        assertEquals("", session.valueText())
    }

    // ------------------------------------------------- operande gauche manquant, signe

    @Test
    fun `un operateur avant tout nombre prend zero pour operande gauche`() {
        // Comme sur une machine de poche : l'afficheur montre 0, l'operateur s'y applique.
        assertEquals(Rational.of(-3), SimpleSession().pressing(Operator.MINUS).type("3").result())
        assertEquals(Rational.ZERO, SimpleSession().pressing(Operator.TIMES).type("3").result())
        assertEquals(Rational.of(3), SimpleSession().pressing(Operator.PLUS).type("3").result())
        assertEquals("0", SimpleSession().pressing(Operator.MINUS).valueText())
    }

    @Test
    fun `le signe s'applique au resultat affiche quand rien n'est tape`() {
        val negated = type("5").equalling().negate()

        assertEquals(Rational.of(-5), negated.accumulator)
        assertEquals("-5", negated.valueText())
        // Et le calcul se poursuit depuis la valeur signee.
        assertEquals(Rational.of(-2), negated.pressing(Operator.PLUS).type("3").result())
    }

    // ------------------------------------------------------------------ utilitaires

    private fun type(digits: String): SimpleSession = SimpleSession().type(digits)

    private fun SimpleSession.type(digits: String): SimpleSession =
        digits.fold(this) { session, digit ->
            if (digit == '.') session.appendSeparator() else session.appendDigit(digit)
        }

    /** [operator] en supposant le succès : l'échec est vérifié explicitement ailleurs. */
    private fun SimpleSession.pressing(operator: Operator): SimpleSession {
        val outcome = operator(operator)
        assertNull(outcome.error, "${operator.symbol} ne devrait pas echouer ici")
        return outcome.session
    }

    private fun SimpleSession.equalling(): SimpleSession {
        val outcome = evaluate()
        assertNull(outcome.error, "= ne devrait pas echouer ici")
        return outcome.session
    }

    /** Le résultat du calcul en cours, l'opération en attente résolue. */
    private fun SimpleSession.result(): Rational? = equalling().accumulator

    private fun SimpleSession.persisted(): SimpleSession = SimpleSession.restore(
        entryKeys = entryKeys(),
        accumulatorKeys = accumulatorKeys(),
        pendingKeys = pendingKeys(),
        entryNegative = entryNegative,
        notation = notation,
    )

    private fun SimpleSession.valueText(): String = text(ExpressionDisplay.DECIMAL)

    private fun SimpleSession.text(display: ExpressionDisplay): String = renderValue(display).text
}
