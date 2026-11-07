package eu.ttbox.gabuzomeu.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.ui.GameTags
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphText
import eu.ttbox.gabuzomeu.ui.theme.DisplayTypography
import kotlin.random.Random

/**
 * Le jeu : reconnaître les nombres Shadok.
 *
 * On y apprend en lisant, pas en éliminant : les leurres sont les confusions réelles, décrites
 * dans [ShadokQuiz]. L'écran, lui, ne fait que deux choses — montrer la question dans le bon sens
 * et donner un retour immédiat.
 *
 * Le retour importe autant que la question : une mauvaise réponse affiche la bonne **en noms**,
 * seule écriture prononçable, plutôt que de se contenter d'un refus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadokQuizScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)

    // La partie survit à la rotation, au changement de thème ou de langue et à la mort du
    // processus — perdre son score en tournant le téléphone serait une punition. Elle n'est en
    // revanche pas mémorisée entre deux ouvertures : chaque partie repart à zéro, ce qui évite
    // de conserver un score dont personne n'a demandé la persistance.
    val random = remember { Random.Default }
    var game by rememberSaveable(stateSaver = QuizGameSaver) {
        mutableStateOf(QuizGame(ShadokQuiz.nextQuestion(random, mastery = 0)))
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(GameTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag(GameTags.CLOSE)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.game_close),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(insets)
                // Défilant : quatre boutons, une question et un verdict ne tiennent pas
                // toujours d'un seul écran — police système agrandie, petit appareil, ou
                // paysage. Sans cela, la dernière réponse serait hors d'atteinte.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Scoreboard(game)
            HorizontalDivider()
            Prompt(game.question)
            Choices(game) { chosen -> game = game.answering(chosen) }
            Verdict(game) { game = game.nextRound(random) }
        }
    }
}

/** Réussites et série. La série récompense la régularité, là où le score cumule. */
@Composable
private fun Scoreboard(game: QuizGame) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameTags.SCORE),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.game_score, game.score, game.asked),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.game_streak, game.streak),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** La question : des glyphes à lire, ou un décimal à écrire. */
@Composable
private fun Prompt(question: QuizQuestion) {
    val promptRes = when (question.direction) {
        QuizDirection.READ_SHADOK -> R.string.game_read_prompt
        QuizDirection.WRITE_SHADOK -> R.string.game_write_prompt
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameTags.QUESTION),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(promptRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        when (question.direction) {
            QuizDirection.READ_SHADOK -> ShadokGlyphText(
                expression = ShadokQuiz.glyphsOf(question.answer),
                // TalkBack lit les noms : une forme ne se prononce pas. La question reste
                // donc jouable sans voir — c'est de la lecture, pas de la reconnaissance.
                semanticsLabel = ShadokQuiz.labelsOf(question.answer),
                style = DisplayTypography.glyphs,
                color = MaterialTheme.colorScheme.onSurface,
                operatorColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.heightIn(min = GLYPH_LINE_HEIGHT),
            )

            QuizDirection.WRITE_SHADOK -> Text(
                text = "${question.answer}",
                style = DisplayTypography.glyphs,
                modifier = Modifier.heightIn(min = GLYPH_LINE_HEIGHT),
            )
        }
    }
}

/** Les quatre réponses possibles, écrites dans le sens inverse de la question. */
@Composable
private fun Choices(game: QuizGame, onChoose: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.question.choices.forEachIndexed { index, choice ->
            ChoiceButton(
                game = game,
                index = index,
                choice = choice,
                onChoose = onChoose,
            )
        }
    }
}

@Composable
private fun ChoiceButton(game: QuizGame, index: Int, choice: Int, onChoose: (Int) -> Unit) {
    val question = game.question
    val revealed = game.answered != null
    // Après la réponse, la bonne se met en valeur et le mauvais choix se signale — les deux à
    // la fois, sinon on apprend qu'on s'est trompé sans apprendre la bonne réponse.
    val colors = when {
        revealed && choice == question.answer -> revealColors(
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        )

        revealed && choice == game.answered -> revealColors(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )

        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Button(
        onClick = { onChoose(choice) },
        // Verrouillé une fois répondu : recliquer changerait le score sans changer la question.
        enabled = !revealed,
        colors = colors,
        // Pas de `clearAndSetSemantics` ici, contrairement aux touches du pavé : il effacerait
        // aussi l'état **désactivé** du bouton, que TalkBack a besoin d'annoncer après une
        // réponse. La description vient donc des enfants — le chiffre pour un nombre, et les
        // noms Shadok pour des glyphes, puisque `ShadokGlyphText` porte déjà la sienne.
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameTags.choice(index)),
    ) {
        when (question.direction) {
            // Le sens s'inverse : on lit des glyphes pour répondre un nombre, et l'inverse.
            QuizDirection.READ_SHADOK -> Text(
                text = "$choice",
                style = MaterialTheme.typography.titleLarge,
            )

            QuizDirection.WRITE_SHADOK -> ShadokGlyphText(
                expression = ShadokQuiz.glyphsOf(choice),
                semanticsLabel = ShadokQuiz.labelsOf(choice),
                style = DisplayTypography.labels,
                // Les glyphes sont des vecteurs teintés : contrairement à un `Text`, ils
                // n'héritent pas de la couleur du bouton — il faut la leur donner, et c'est
                // `LocalContentColor` que le bouton vient d'installer pour ses enfants.
                color = LocalContentColor.current,
                operatorColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * Les couleurs d'un choix révélé.
 *
 * Elles valent **aussi pour l'état désactivé** : le bouton est verrouillé dès la réponse, et
 * sans cela Material le repeindrait en gris désactivé — la bonne réponse ne se distinguerait
 * plus des autres, ce qui ôterait au retour tout son intérêt.
 */
@Composable
private fun revealColors(container: Color, content: Color) = ButtonDefaults.buttonColors(
    containerColor = container,
    contentColor = content,
    disabledContainerColor = container,
    disabledContentColor = content,
)

/**
 * Le verdict, et le bouton pour continuer.
 *
 * Il occupe la place même quand il n'y a rien à dire : sans cela, les boutons de réponse
 * sauteraient sous le doigt à chaque question.
 */
@Composable
private fun Verdict(game: QuizGame, onNext: () -> Unit) {
    val answered = game.answered

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VERDICT_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (answered == null) return@Column

        val correct = answered == game.question.answer
        Text(
            text = if (correct) {
                stringResource(R.string.game_correct)
            } else {
                stringResource(R.string.game_wrong, ShadokQuiz.labelsOf(game.question.answer))
            },
            style = MaterialTheme.typography.titleMedium,
            // Annoncé dès qu'il paraît : sans région « live », TalkBack resterait muet et
            // l'on ne saurait pas, sans voir, si l'on a eu juste.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (correct) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        TextButton(onClick = onNext) { Text(stringResource(R.string.game_next)) }
    }
}

private val GLYPH_LINE_HEIGHT = 56.dp
private val VERDICT_HEIGHT = 72.dp
