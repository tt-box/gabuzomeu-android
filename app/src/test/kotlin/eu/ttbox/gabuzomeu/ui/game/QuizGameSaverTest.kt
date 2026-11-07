package eu.ttbox.gabuzomeu.ui.game

import androidx.compose.runtime.saveable.SaverScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * La sauvegarde d'une partie : ce qui permet au score de survivre à une rotation.
 *
 * On vérifie l'aller-retour sans Android : le `Saver` ne produit que des entiers, et c'est le
 * `Bundle` — hors de portée d'un test JVM — qui les garde ensuite.
 */
class QuizGameSaverTest {

    /** Un `SaverScope` qui accepte tout, comme le ferait un registre sur des entiers. */
    private val scope = SaverScope { true }

    private fun roundTrip(game: QuizGame): QuizGame? {
        val saved = with(QuizGameSaver) { scope.save(game) }
        return saved?.let(QuizGameSaver::restore)
    }

    @Test
    fun `une question ouverte revient a l'identique`() {
        val game = QuizGame(
            question = QuizQuestion(
                QuizDirection.READ_SHADOK,
                answer = 6,
                choices = listOf(12, 6, 9, 5),
            ),
            score = 3,
            asked = 5,
            streak = 2,
            answered = null,
            mastery = 3,
        )

        assertEquals(game, roundTrip(game))
    }

    @Test
    fun `une question repondue garde sa reponse et son verdict`() {
        // Le zéro est une réponse comme une autre : il ne doit pas se confondre avec « pas de
        // réponse » à la restauration.
        val game = QuizGame(
            question = QuizQuestion(
                QuizDirection.WRITE_SHADOK,
                answer = 1,
                choices = listOf(0, 1, 4, 2),
            ),
            score = 7,
            asked = 9,
            streak = 0,
            answered = 0,
            mastery = 7,
        )

        assertEquals(game, roundTrip(game))
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 4, 12])
    fun `toute question tiree survit a la sauvegarde`(mastery: Int) {
        val random = Random(mastery)
        repeat(100) {
            val game = QuizGame(ShadokQuiz.nextQuestion(random, mastery), mastery = mastery)
            assertEquals(game, roundTrip(game))
        }
    }
}
