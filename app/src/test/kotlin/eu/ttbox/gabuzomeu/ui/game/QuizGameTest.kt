package eu.ttbox.gabuzomeu.ui.game

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Les transitions d'une partie : répondre, puis passer à la question suivante. */
class QuizGameTest {

    private val question = QuizQuestion(
        QuizDirection.READ_SHADOK,
        answer = 6,
        choices = listOf(12, 6, 9, 5),
    )

    private val game = QuizGame(question = question, score = 2, asked = 3, streak = 1, mastery = 2)

    @Test
    fun `une bonne reponse fait monter le score, la serie, la maitrise et le compte`() {
        val next = game.answering(6)

        assertEquals(3, next.score)
        assertEquals(2, next.streak)
        assertEquals(3, next.mastery)
        assertEquals(4, next.asked)
        assertEquals(6, next.answered)
    }

    @Test
    fun `une mauvaise reponse casse la serie sans faire reculer la maitrise`() {
        val next = game.answering(12)

        assertEquals(2, next.score)
        assertEquals(0, next.streak)
        assertEquals(2, next.mastery)
        assertEquals(4, next.asked)
        assertEquals(12, next.answered)
    }

    @Test
    fun `la question reste en place apres la reponse`() {
        assertEquals(question, game.answering(6).question)
    }

    @Test
    fun `une seconde reponse sur une question deja repondue ne change rien`() {
        val answered = game.answering(12)

        assertSame(answered, answered.answering(6))
    }

    @Test
    fun `la question suivante efface la reponse et garde le score`() {
        val answered = game.answering(6)

        val next = answered.nextRound(Random(7))

        assertNull(next.answered)
        assertEquals(answered.score, next.score)
        assertEquals(answered.asked, next.asked)
        assertEquals(answered.streak, next.streak)
        assertEquals(answered.mastery, next.mastery)
    }

    @Test
    fun `la question suivante est tiree au palier atteint`() {
        val next = game.copy(mastery = 0).nextRound(Random(1))

        assertTrue(next.question.answer <= ShadokQuiz.ceilingFor(0))
    }
}
