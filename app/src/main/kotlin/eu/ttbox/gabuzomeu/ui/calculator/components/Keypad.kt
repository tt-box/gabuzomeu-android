package eu.ttbox.gabuzomeu.ui.calculator.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.ui.KeypadTags
import eu.ttbox.gabuzomeu.ui.calculator.KeyAction
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphs
import eu.ttbox.gabuzomeu.ui.theme.DisplayTypography

/**
 * Le pavé de touches.
 *
 * Remplace à lui seul `ColorButton` (qui castait son `Context` en `Calculator`, donc
 * inutilisable ailleurs), `EventListener` (qui routait les appuis **par le texte du
 * bouton**), et les huit fichiers de disposition XML par orientation.
 */
@Composable
fun Keypad(
    mode: CalculationMode,
    notation: NumberNotation,
    onKey: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = KeypadLayout.forMode(mode, notation)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Les rangées se partagent la hauteur tant que chacune garde la taille d'une cible
        // tactile. En deçà — téléphone couché, sept rangées NPI — le pavé défile plutôt que
        // d'écraser ses touches sous les 48 dp.
        val shared = (maxHeight - KEY_SPACING * (rows.size - 1)) / rows.size
        val fits = maxHeight == Dp.Infinity || shared >= MIN_KEY_HEIGHT
        Column(
            modifier = if (fits) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            },
            verticalArrangement = Arrangement.spacedBy(KEY_SPACING),
        ) {
            rows.forEach { row ->
                KeyRow(
                    row = row,
                    onKey = onKey,
                    modifier = if (fits) Modifier.weight(1f) else Modifier.height(MIN_KEY_HEIGHT),
                )
            }
        }
    }
}

@Composable
private fun KeyRow(row: List<KeySpec>, onKey: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
    ) {
        row.forEach { key ->
            KeyButton(
                key = key,
                onKey = onKey,
                modifier = Modifier
                    .weight(key.weight)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun KeyButton(key: KeySpec, onKey: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val description = when {
        key.glyph != null -> key.glyph.label
        key.contentDescriptionRes != null -> stringResource(key.contentDescriptionRes)
        else -> key.text.orEmpty()
    }

    // clearAndSetSemantics : TalkBack annonce « Zo », jamais la forme « ⅃ ».
    //
    // Conséquence à connaître : cela efface aussi la sémantique de texte des
    // descendants, donc une touche n'est PAS trouvable par `onNodeWithText`. D'où le
    // testTag, posé dans le même bloc — un repère stable, indépendant de la langue.
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = description
        testTag = KeypadTags.of(key.action)
    }

    val content: @Composable () -> Unit = {
        if (key.glyph != null) {
            // La glose « Ga (0) » sous le glyphe : c'est elle qui fait du pavé Simple un
            // pavé qui s'explique. Elle n'est pas répétée à TalkBack — le
            // clearAndSetSemantics ci-dessus annonce déjà « Ga », et « Ga Ga 0 » serait
            // une lecture pénible pour rien.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = ShadokGlyphs.of(key.glyph),
                    contentDescription = null,
                    modifier = Modifier.size(GLYPH_KEY_SIZE),
                )
                key.subLabel?.let { digit ->
                    Text(
                        text = stringResource(R.string.key_digit_gloss, digit.label, digit.value),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = MIN_GLOSS_FONT_SIZE,
                            maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                        ),
                    )
                }
            }
        } else {
            Text(
                text = key.text.orEmpty(),
                // Les opérateurs en gras : dans Roboto, « − » (U+2212) est nettement plus fin
                // que « + », « × » et « ÷ », si bien qu'il paraissait moins présent que ses
                // voisins sur la même rangée. La graisse rattrape l'écart sans toucher aux
                // chiffres, dont l'épaisseur, elle, est déjà cohérente.
                style = if (key.kind == KeyKind.OPERATOR) {
                    DisplayTypography.key.copy(fontWeight = FontWeight.Bold)
                } else {
                    DisplayTypography.key
                },
                maxLines = 1,
                // La touche s'adapte au libellé plutôt que de le rogner : « POMPER ↵ » ne
                // tient pas à la taille nominale sur une touche étroite, et le repli
                // protège tous les libellés quand la police du système est agrandie.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MIN_KEY_FONT_SIZE,
                    maxFontSize = DisplayTypography.key.fontSize,
                ),
            )
        }
    }

    Button(
        onClick = { onKey(key.action) },
        modifier = modifier.then(semantics),
        // La palette de l'affiche l'emporte là où elle est posée — le pavé Simple, et lui
        // seul. Partout ailleurs, les rôles Material du thème, couleur dynamique comprise.
        colors = key.palette?.colors() ?: key.kind.colors(),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) { content() }
}

/**
 * Couleurs d'une touche.
 *
 * Conteneur **et** contenu sont toujours déclarés ensemble, en respectant les couples de
 * rôles Material 3 (`xContainer` / `onXContainer`). Ne fixer que `containerColor` laisse
 * `contentColor` à sa valeur par défaut, celle d'un *autre* rôle : le résultat est un
 * symbole quasi illisible sur son fond — c'était le cas de la rangée de fonctions en
 * thème sombre, verdâtre sur lavande.
 */
@Composable
private fun KeyKind.colors(): ButtonColors = when (this) {
    KeyKind.EQUALS -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )

    KeyKind.OPERATOR -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    KeyKind.FUNCTION -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    KeyKind.DIGIT -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

private val KEY_SPACING = 8.dp

/** La taille minimale d'une cible tactile selon Material et les règles d'accessibilité. */
private val MIN_KEY_HEIGHT = 48.dp
private val GLYPH_KEY_SIZE = 30.dp

/** Plancher du repli typographique : en dessous, une touche cesse d'être lisible. */
private val MIN_KEY_FONT_SIZE = 12.sp

/**
 * Plancher de la glose « Ga (0) ».
 *
 * Plus bas que celui des libellés : la glose est une annotation, elle a le droit d'être
 * petite. En dessous de 8 sp, en revanche, elle ne s'adresse plus à personne.
 */
private val MIN_GLOSS_FONT_SIZE = 8.sp
