package eu.ttbox.gabuzomeu.ui.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.ui.HelpTags

/**
 * « Comprendre les Shadoks » — la leçon du professeur Shadoko, dans l'application.
 *
 * Elle est racontée **dans ses termes à lui**, ceux de la vidéo d'origine : au-delà de MEU, on
 * jette les Shadoks dans une poubelle, puis les poubelles dans une grande poubelle. Ce détour
 * par les poubelles n'est pas du folklore — c'est la retenue, énoncée de façon qu'on la voie.
 * La version précédente disait « il faut ajouter un rang », ce qui n'apprend rien à qui ne sait
 * pas déjà ce qu'est un rang.
 *
 * Deux principes tiennent le reste :
 *
 * - **le lien vers la vidéo est en tête**, avant la moindre explication. C'est elle, la leçon ;
 *   cette page n'en est que la transcription. Reléguée en pied de page, il fallait défiler tout
 *   l'écran pour la découvrir ;
 * - **ne rien écrire à la main de ce que le moteur sait calculer.** Les deux tables sont dans
 *   `HelpTables.kt` et se calculent : elles ne peuvent donc pas mentir sur ce que la calculatrice
 *   fera. C'est la discipline déjà appliquée aux miniatures des widgets, engendrées depuis les
 *   mêmes tracés que le rendu.
 */
// `TopAppBar` reste marqué expérimental dans Material 3, comme la classe de taille de fenêtre
// dont MainActivity dépend déjà. L'alternative serait de réimplémenter à la main la hauteur, les
// encarts système et la sémantique d'une barre de titre : plus de code, et moins juste.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadokHelpScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(HelpTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag(HelpTags.CLOSE),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.help_close),
                        )
                    }
                },
            )
        },
    ) { insets ->
        HelpContent(modifier = Modifier.padding(insets))
    }
}

/** La leçon, dans l'ordre où le professeur la donne. Défilante : elle fait plusieurs écrans. */
@Composable
private fun HelpContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VideoLink()
        TheProblem()
        TheFourWords()
        TheBins()
        Section(R.string.help_table_title)
        NumberTable()
        WhatTheCalculatorAdds()
        Ending()
    }
}

/**
 * Le lien vers la leçon d'origine, en accès direct.
 *
 * Un lien sortant plutôt qu'une illustration embarquée : il ne pèse rien et pointe vers la
 * chaîne de l'ayant droit, qui diffuse la vidéo lui-même. Le titre de la vidéo, le nom de la
 * chaîne et la destination sont écrits en toutes lettres — jamais un bouton opaque : personne
 * ne doit quitter l'application sans le savoir.
 */
@Composable
private fun VideoLink() {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            // Le `clickable` est **dans** la Surface : posé par-dessus, son ondulation
            // déborderait des coins que la Surface découpe. Il précède le `padding`, si bien
            // que la marge fait partie de la cible.
            modifier = Modifier
                .clickable {
                    // Il n'y a pas toujours une application pour l'ouvrir : appareil sans
                    // navigateur, profil restreint, navigateur désactivé. Sans ce garde-fou,
                    // toucher le lien ferait planter l'aide ; on le dit plutôt.
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_VIDEO_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.help_video_unavailable, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                .padding(16.dp)
                .testTag(HelpTags.VIDEO),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                // La ligne entière s'annonce par son texte ; l'icône n'a rien à y ajouter.
                contentDescription = null,
                modifier = Modifier.size(PLAY_SIZE),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.help_video_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.help_video_channel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.help_video_hint),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Pourquoi il a fallu réformer : quatre mots, et rien au-delà. */
@Composable
private fun TheProblem() {
    Section(R.string.help_problem_title)
    Body(R.string.help_problem_body)
}

/** « On dit… et on écrit… » : la citation, puis les quatre chiffres qu'elle nomme. */
@Composable
private fun TheFourWords() {
    Section(R.string.help_digits_title)
    Text(
        text = stringResource(R.string.help_quote),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    ShadokDigit.entries.forEach { DigitRow(it) }
}

/** La retenue, expliquée comme dans la leçon : une poubelle, puis des poubelles de poubelles. */
@Composable
private fun TheBins() {
    Section(R.string.help_bin_title)
    Body(R.string.help_bin_body)
    Body(R.string.help_bin_more)

    Section(R.string.help_bins_title)
    Body(R.string.help_bins_body)
    // La règle arithmétique vient **après** l'image, et pas à sa place : « chaque rang vaut
    // quatre fois le précédent » ne se comprend bien qu'une fois les poubelles vues.
    Body(R.string.help_bins_rule)
}

/** Ce que la leçon ne couvre pas, et que la calculatrice a bien fallu trancher. */
@Composable
private fun WhatTheCalculatorAdds() {
    Section(R.string.help_fractions_title)
    Body(R.string.help_fractions_body)

    Section(R.string.help_clock_title)
    Body(R.string.help_clock_body)
}

/** La fin de la leçon, telle qu'elle se termine dans la vidéo. */
@Composable
private fun Ending() {
    Section(R.string.help_ending_title)
    Text(
        text = stringResource(R.string.help_ending_body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(HelpTags.ENDING),
    )
}

@Composable
private fun Section(titleRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Un paragraphe de la leçon. Tout le texte courant de l'écran passe par ici. */
@Composable
private fun Body(textRes: Int) {
    Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
}

/** « Comment compter comme les Shadoks ? », chaîne Archive INA. */
private const val SOURCE_VIDEO_URL = "https://www.youtube.com/watch?v=lP9PaDs2xgQ"

private val PLAY_SIZE = 32.dp
