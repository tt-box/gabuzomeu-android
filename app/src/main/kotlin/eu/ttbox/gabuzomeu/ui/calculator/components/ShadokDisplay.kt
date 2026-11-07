package eu.ttbox.gabuzomeu.ui.calculator.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.EvalError
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.ui.DisplayTags
import eu.ttbox.gabuzomeu.ui.calculator.CalculatorUiState
import eu.ttbox.gabuzomeu.ui.calculator.StackLevel
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphText
import eu.ttbox.gabuzomeu.ui.shadok.ShadokLabelText
import eu.ttbox.gabuzomeu.ui.theme.DisplayTypography

/**
 * L'afficheur : les glyphes Shadok en vedette, puis les noms, puis le décimal.
 *
 * Contrairement au projet d'origine, ce ne sont pas des champs éditables synchronisés
 * entre eux mais des projections en lecture seule du même tampon. Tout l'échafaudage de
 * `CalculatorEditText` (256 lignes : suppression de l'IME, `NoTextSelectionMode`, menu
 * contextuel copier/coller maison) et de `CalculatorDisplay` (`ViewSwitcher` +
 * `TranslateAnimation`) disparaît : sans champ de saisie, il n'y a plus de clavier
 * système à combattre.
 *
 * Les deux lignes secondaires se masquent depuis les réglages ; la ligne de glyphes,
 * elle, est toujours là.
 */
@Composable
fun ShadokDisplay(
    state: CalculatorUiState,
    modifier: Modifier = Modifier,
    onPaste: (String) -> Unit = {},
    onCopied: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Un `when` et non une cascade de `if` : au quatrième mode de calcul, ce sera le
        // compilateur qui rappellera qu'il lui faut un afficheur.
        when (state.mode) {
            CalculationMode.RPN -> RpnDisplay(state, onPaste, onCopied)

            CalculationMode.SIMPLE -> SimpleDisplay(state, onPaste, onCopied)

            CalculationMode.CLASSIC -> {
                // Un résultat est une valeur : on le lit par sa tête. Une expression en cours
                // de frappe se suit au contraire par sa queue, là où arrive le chiffre tapé.
                val scrollToEnd = !state.showingResult
                ValueActions(state, onPaste, onCopied) {
                    ValueLines(state, scrollToEnd)
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                // Région vivante : TalkBack lit l'erreur dès qu'elle paraît, sans que
                // l'utilisateur ait à quitter le pavé pour aller la chercher.
                modifier = Modifier
                    .testTag(DisplayTags.ERROR)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Les trois écritures d'une valeur, empilées : glyphes, noms, décimal.
 *
 * Partagée par le mode classique et le mode Simple — dans les deux cas, la grande valeur est
 * une valeur unique. Seule la NPI a besoin d'autre chose, ayant une pile à montrer sous elle.
 */
@Composable
internal fun ValueLines(state: CalculatorUiState, scrollToEnd: Boolean) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphLine(state, scrollToEnd = scrollToEnd)
        if (state.settings.showShadokLabels) LabelLine(state, scrollToEnd)
        if (state.settings.showDecimal) DecimalLine(state, scrollToEnd)
    }
}

/**
 * La valeur principale, rendue copiable.
 *
 * Un seul point d'ancrage pour les deux modes : en NPI c'est X, en classique l'expression
 * entière — dans les deux cas, ce que l'utilisateur désigne quand il appuie sur l'afficheur.
 */
@Composable
internal fun ValueActions(
    state: CalculatorUiState,
    onPaste: (String) -> Unit,
    onCopied: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisplayActions(
        value = CopyableValue.of(state),
        tag = DisplayTags.ACTIONS,
        modifier = Modifier.fillMaxWidth(),
        onPaste = onPaste,
        onCopied = onCopied,
        content = content,
    )
}

/**
 * Ligne principale : les glyphes Shadok, l'identité de l'application.
 *
 * @param scrollToEnd un nombre plus large que l'écran montre sa queue plutôt que sa tête.
 *   C'est ce qu'il faut pendant la frappe — on veut voir le chiffre qu'on vient de taper —
 *   et l'inverse de ce qu'il faut pour une valeur, dont les rangs de tête disent la valeur.
 */
@Composable
internal fun GlyphLine(
    state: CalculatorUiState,
    scrollToEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    DisplayLine(
        approximate = state.shadokApproximate,
        style = DisplayTypography.glyphs,
        color = color,
        modifier = modifier,
    ) { lineModifier ->
        ShadokGlyphText(
            expression = state.glyphs,
            // TalkBack lit les noms, pas les formes.
            semanticsLabel = state.labels.ifEmpty { stringResource(R.string.display_empty) },
            style = DisplayTypography.glyphs,
            color = color,
            operatorColor = MaterialTheme.colorScheme.tertiary,
            modifier = lineModifier
                .testTag(DisplayTags.GLYPHS)
                .then(if (state.showingResult) ResultLiveRegion else Modifier)
                .horizontalScroll(rememberScrollState(), reverseScrolling = scrollToEnd),
        )
    }
}

/** Deuxième ligne : les noms prononcés — « quand il y a encore un shadok de plus… ». */
@Composable
internal fun LabelLine(
    state: CalculatorUiState,
    scrollToEnd: Boolean,
    showApproximation: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    DisplayLine(
        approximate = state.shadokApproximate && showApproximation,
        style = DisplayTypography.labels,
        color = color,
    ) { lineModifier ->
        ShadokLabelText(
            expression = state.labels,
            style = DisplayTypography.labels,
            color = color,
            operatorColor = MaterialTheme.colorScheme.tertiary,
            modifier = lineModifier
                .testTag(DisplayTags.LABELS)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = scrollToEnd),
        )
    }
}

/** Ligne secondaire : la traduction décimale. */
@Composable
internal fun DecimalLine(state: CalculatorUiState, scrollToEnd: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    DisplayLine(
        approximate = state.decimalApproximate,
        style = DisplayTypography.decimal,
        color = color,
    ) { lineModifier ->
        AnimatedContent(
            targetState = state.decimal,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "decimal",
            modifier = lineModifier,
        ) { text ->
            Text(
                text = text,
                style = DisplayTypography.decimal,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .testTag(DisplayTags.DECIMAL)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState(), reverseScrolling = scrollToEnd),
            )
        }
    }
}

/**
 * Une ligne d'affichage, avec son marqueur d'approximation **hors** de la zone défilante.
 *
 * C'est le point important : une ligne trop longue en cache forcément une partie, tête ou
 * queue selon le sens de défilement. Un `≈` placé dans le texte lui-même disparaîtrait donc
 * avec elle — et un marqueur invisible ne sert à rien. Ancré ici dans un `Row`, il reste
 * visible quelle que soit la longueur du nombre et quel que soit le bout qu'on regarde.
 */
@Composable
private fun DisplayLine(
    approximate: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (approximate) {
            Text(
                text = ShadokFormatter.APPROXIMATION.toString(),
                style = style,
                color = color,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        content(Modifier.weight(1f))
    }
}

/**
 * Un résultat affiché est annoncé par TalkBack sans qu'on aille le chercher.
 *
 * Seulement le résultat : en région vivante permanente, la ligne serait relue en entier à
 * chaque chiffre tapé, alors que la touche elle-même vient déjà d'être annoncée.
 */
private val ResultLiveRegion = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

private fun EvalError.messageRes(): Int = when (this) {
    EvalError.EMPTY -> R.string.error_empty
    EvalError.SYNTAX -> R.string.error_syntax
    EvalError.UNBALANCED_PARENTHESES -> R.string.error_parentheses
    EvalError.DIVISION_BY_ZERO -> R.string.error_division_by_zero
    EvalError.STACK_UNDERFLOW -> R.string.error_stack_underflow
}
