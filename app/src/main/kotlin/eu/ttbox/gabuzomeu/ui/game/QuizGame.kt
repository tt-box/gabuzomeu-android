package eu.ttbox.gabuzomeu.ui.game

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlin.random.Random

/**
 * L'état d'une partie. Immuable, comme le reste du projet : chaque réponse en produit un nouveau.
 *
 * @property mastery le nombre de bonnes réponses, qui pilote la difficulté. Distinct de [score]
 *   à dessein — ils sont égaux aujourd'hui, mais l'un est une règle de jeu et l'autre un
 *   affichage, et les confondre interdirait de changer l'un sans l'autre.
 */
internal data class QuizGame(
    val question: QuizQuestion,
    val score: Int = 0,
    val asked: Int = 0,
    val streak: Int = 0,
    /** La réponse donnée, ou `null` tant que la question est ouverte. */
    val answered: Int? = null,
    val mastery: Int = 0,
)

/** Enregistre une réponse. La question reste, avec son verdict. */
internal fun QuizGame.answering(chosen: Int): QuizGame {
    if (answered != null) return this
    val correct = chosen == question.answer
    return copy(
        answered = chosen,
        asked = asked + 1,
        score = if (correct) score + 1 else score,
        // La série se casse net ; la maîtrise, elle, ne régresse pas — se tromper redonne une
        // chance au même palier plutôt que de renvoyer en arrière.
        streak = if (correct) streak + 1 else 0,
        mastery = if (correct) mastery + 1 else mastery,
    )
}

/** Passe à la question suivante, au palier atteint. */
internal fun QuizGame.nextRound(random: Random): QuizGame =
    copy(question = ShadokQuiz.nextQuestion(random, mastery), answered = null)

/**
 * Sauvegarde d'une partie, à plat et en entiers seulement — ce qu'un `Bundle` sait garder.
 *
 * Ordre : sens, réponse, score, questions posées, série, réponse donnée ([NOT_ANSWERED] si la
 * question est ouverte), maîtrise, puis les choix proposés. Les choix sont gardés tels quels
 * plutôt que retirés : la question doit rester **la même** après une rotation.
 */
internal val QuizGameSaver: Saver<QuizGame, Any> = listSaver(
    save = { game ->
        listOf(
            game.question.direction.ordinal,
            game.question.answer,
            game.score,
            game.asked,
            game.streak,
            game.answered ?: NOT_ANSWERED,
            game.mastery,
        ) + game.question.choices
    },
    restore = { saved ->
        QuizGame(
            question = QuizQuestion(
                direction = QuizDirection.entries[saved[0]],
                answer = saved[1],
                choices = saved.drop(SAVED_HEADER_SIZE),
            ),
            score = saved[2],
            asked = saved[3],
            streak = saved[4],
            answered = saved[5].takeIf { it != NOT_ANSWERED },
            mastery = saved[6],
        )
    },
)

/** Les réponses sont toujours positives : -1 ne peut donc pas être une réponse donnée. */
private const val NOT_ANSWERED = -1
private const val SAVED_HEADER_SIZE = 7
