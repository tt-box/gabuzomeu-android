package eu.ttbox.gabuzomeu.ui.calculator

import eu.ttbox.gabuzomeu.core.eval.EvalError
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.core.eval.SimpleSession
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le routage des touches du mode Simple, sans ViewModel. */
class SimpleKeysTest {

    private val decimal = SimpleSession.of(NumberNotation.DECIMAL)

    private fun SimpleSession.typed(vararg actions: KeyAction): SimpleSession =
        actions.fold(this) { session, action -> session.handle(action).session }

    private fun digit(c: Char) = KeyAction.Digit(c)

    @Test
    fun `le separateur est transmis a la frappe`() {
        val session = decimal.typed(digit('1'), KeyAction.Separator, digit('5'))

        assertEquals("1.5", session.entryKeys())
    }

    @Test
    fun `effacer retire le dernier caractere de la frappe`() {
        val session = decimal.typed(digit('1'), digit('2'), KeyAction.Delete)

        assertEquals("1", session.entryKeys())
    }

    @Test
    fun `tout effacer remet la machine au repos`() {
        val session = decimal.typed(
            digit('1'),
            KeyAction.Op(Operator.PLUS),
            digit('2'),
            KeyAction.Clear,
        )

        assertTrue(session.isPristine)
    }

    @Test
    fun `le signe change la frappe en cours`() {
        val session = decimal.typed(digit('4'), KeyAction.Negate)

        assertTrue(session.entryNegative)
    }

    @Test
    fun `les touches absentes du pave Simple rendent la meme session`() {
        val session = decimal.typed(digit('4'))

        listOf(
            KeyAction.LeftParen,
            KeyAction.RightParen,
            KeyAction.Enter,
            KeyAction.Swap,
            KeyAction.Drop,
        ).forEach { action ->
            val outcome = session.handle(action)
            assertEquals(session, outcome.session, "action $action")
            assertNull(outcome.error)
        }
    }

    @Test
    fun `une division par zero garde la session d'avant intacte`() {
        val before = decimal.typed(digit('5'), KeyAction.Op(Operator.DIVIDE), digit('0'))

        val outcome = before.handle(KeyAction.Evaluate)

        assertEquals(EvalError.DIVISION_BY_ZERO, outcome.error)
        assertEquals(before, outcome.session)
    }

    @Test
    fun `un calcul simple s'evalue`() {
        val session = decimal.typed(
            digit('2'),
            KeyAction.Op(Operator.PLUS),
            digit('3'),
            KeyAction.Evaluate,
        )

        assertTrue(session.showingResult)
    }
}
