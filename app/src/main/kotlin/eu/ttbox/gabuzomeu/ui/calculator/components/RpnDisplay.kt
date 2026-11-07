package eu.ttbox.gabuzomeu.ui.calculator.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.ui.DisplayTags
import eu.ttbox.gabuzomeu.ui.calculator.CalculatorUiState
import eu.ttbox.gabuzomeu.ui.calculator.StackLevel
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphText
import eu.ttbox.gabuzomeu.ui.theme.DisplayTypography

/**
 * Écart entre la colonne des décimales et celle des Shadok.
 *
 * Partagé par les niveaux de pile et par la grande ligne : c'est ce qui fait que les deux
 * colonnes se lisent d'un seul trait de part et d'autre du séparateur.
 */
private val VALUE_COLUMN_SPACING = 12.dp

/**
 * Largeur de la colonne des rangs, réservée **même quand le rang est absent**.
 *
 * Une frappe n'a pas de rang, mais si sa colonne disparaissait, son nombre glisserait d'un
 * cran par rapport à la pile — et ce décalage se lirait comme le déplacement que seul ENTER
 * a le droit de produire.
 */
private val LEVEL_COLUMN_WIDTH = 34.dp

/** Le rang du sommet de la pile, celui qui touche le séparateur. */
private const val TOP_LEVEL = 1

/**
 * L'afficheur en notation polonaise inverse : la pile au-dessus du trait, la frappe dessous.
 *
 * Le trait n'est pas décoratif, c'est la frontière de la pile — et **le grand affichage
 * change de côté** selon qu'il montre une valeur empilée ou un nombre encore sous le doigt.
 * ENTER fait donc franchir le trait au nombre, qui reçoit du même coup son rang.
 *
 * Cette bascule est ce qui manquait. La grande ligne valant « la frappe, ou à défaut le
 * sommet », `6` tapé et `6` empilé se dessinaient identiquement : le premier ENTER ne se
 * voyait pas. On appuyait une seconde fois, la convention HP dupliquait le sommet, et un `6`
 * fantôme restait au fond de la pile pour tout le calcul suivant.
 *
 * Un seul bloc est grand à la fois — la frappe tant qu'on tape, le sommet sinon : c'est
 * toujours la valeur sur laquelle la prochaine touche agira.
 *
 * Ce qui bouge est le trait, pas le nombre, et ce n'est pas le premier choix. Garder le trait
 * fixe demandait de dessiner sous lui une zone de frappe vide de la hauteur d'une valeur —
 * un bloc entier de gagné par le nombre, mais perdu par la pile. Sur un écran de 360 × 640,
 * l'afficheur n'a que 227 dp : deux blocs et un trait n'y tiennent pas, et **la pile
 * disparaissait purement et simplement**. Le nombre reste donc à sa place et c'est la
 * frontière qui passe de l'autre côté de lui — le rapport des deux change à l'identique,
 * pour rien.
 */
@Composable
internal fun ColumnScope.RpnDisplay(
    state: CalculatorUiState,
    onPaste: (String) -> Unit,
    onCopied: () -> Unit,
) {
    val onStack = !state.entering && !state.isEmpty

    RpnStackArea(
        levels = state.stack,
        // Pendant une frappe, la pile entière est dans la liste et son sommet y est donc le
        // niveau 1. Sinon ce sommet occupe le grand affichage, et la liste reprend à 2.
        firstLevel = if (onStack) TOP_LEVEL + 1 else TOP_LEVEL,
        showDecimal = state.settings.showDecimal,
        onCopied = onCopied,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    )

    if (onStack) {
        ValueActions(state, onPaste, onCopied) { XValue(state, level = TOP_LEVEL) }
    }

    HorizontalDivider(modifier = Modifier.testTag(DisplayTags.STACK_LIMIT))

    // Rien sous le trait quand rien n'est tapé : la place revient à la pile, qui montre un
    // niveau de plus. C'est le trait qui aura remonté d'un bloc.
    if (!onStack) {
        ValueActions(state, onPaste, onCopied) { XValue(state, level = null) }
    }
}

/**
 * La grande valeur : décimal et glyphes **sur la même ligne**, les noms en dessous.
 *
 * La première ligne reprend exactement le partage des niveaux de pile — rang, décimal à
 * gauche, Shadok à droite, mêmes largeurs — si bien que les colonnes se lisent d'un seul
 * trait du fond de pile jusqu'au calcul en cours. Les noms prononcés viennent ensuite, sur
 * toute la largeur : ce sont eux qui s'allongent le plus (`MeuZoGa` pour trois chiffres).
 *
 * @param level le rang si la valeur est empilée, `null` si c'est la frappe en cours.
 */
@Composable
private fun XValue(state: CalculatorUiState, level: Int?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VALUE_COLUMN_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelMarker(
                level = level,
                // Repère posé **seulement quand le rang existe** : sa présence est ce qu'un
                // test interroge pour savoir si la grande valeur est dans la pile.
                modifier = if (level == null) Modifier else Modifier.testTag(DisplayTags.X_LEVEL),
            )
            if (state.settings.showDecimal) {
                XDecimal(state, modifier = Modifier.weight(1f))
            }
            GlyphLine(state, scrollToEnd = state.entering, modifier = Modifier.weight(1f))
        }
        // Le « ≈ » du Shadok est déjà porté par la ligne de glyphes : le répéter sur les
        // noms le ferait paraître deux fois pour une seule et même valeur.
        if (state.settings.showShadokLabels) {
            LabelLine(state, scrollToEnd = state.entering, showApproximation = false)
        }
    }
}

/** Le décimal de la grande valeur, à gauche, son marqueur `≈` ancré hors du défilement. */
@Composable
private fun XDecimal(state: CalculatorUiState, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (state.decimalApproximate) {
            Text(
                text = ShadokFormatter.APPROXIMATION.toString(),
                style = DisplayTypography.decimal,
                color = color,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        AnimatedContent(
            targetState = state.decimal,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "decimal-x",
            modifier = Modifier.weight(1f),
        ) { text ->
            Text(
                text = text,
                style = DisplayTypography.decimal,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .testTag(DisplayTags.DECIMAL)
                    .fillMaxWidth()
                    // Une frappe se suit par sa queue — on regarde le chiffre qu'on vient
                    // de taper. Une valeur, elle, se lit par sa tête.
                    .horizontalScroll(rememberScrollState(), reverseScrolling = state.entering),
            )
        }
    }
}

/**
 * Les niveaux de pile qui ne sont **pas** dans le grand affichage.
 *
 * `reverseLayout` fait deux choses d'un coup : le sommet se colle au séparateur et la liste
 * reste ancrée en bas quand la pile grandit — un empilement de dix valeurs ne pousse pas le
 * calcul en cours hors de l'écran. Les niveaux profonds défilent vers le haut, sans limite.
 *
 * Chaque niveau porte ses glyphes et son décimal, mais pas ses noms : trois écritures par
 * ligne saturerait la hauteur, et c'est la grande valeur qui a besoin du détail complet.
 *
 * @param firstLevel le rang de la ligne du bas — voir [RpnDisplay].
 * @param showDecimal le réglage d'affichage du décimal. Il vaut pour toute la colonne, pile
 *   comprise : masquer le décimal en bas et le laisser sur les niveaux au-dessus donnerait
 *   un afficheur mi-figue mi-raisin.
 */
@Composable
private fun RpnStackArea(
    levels: List<StackLevel>,
    firstLevel: Int,
    showDecimal: Boolean,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.testTag(DisplayTags.STACK),
        reverseLayout = true,
        horizontalAlignment = Alignment.End,
        // `Alignment.Bottom` explicite : `reverseLayout` inverse l'ordre des niveaux mais
        // laisse `spacedBy` les tasser en **haut** de la zone, faute d'alignement précisé.
        // La pile flottait donc loin du trait — et depuis qu'elle a des rangs, le sommet
        // s'en trouvait détaché des siens : « 3 : » et « 2 : » en l'air, « 1 : » tout seul
        // en bas. Une pile se lit d'un bloc.
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
    ) {
        // La liste est inversée comme la disposition : l'indice 0 est donc le niveau
        // immédiatement sous le grand affichage.
        itemsIndexed(levels.asReversed()) { depth, level ->
            // Chaque niveau est copiable pour lui-même : une valeur enfouie sous trois autres
            // se récupère sans avoir à la ramener au sommet. Coller, en revanche, n'a pas de
            // sens ici — on n'écrit pas au milieu d'une pile — donc l'item n'apparaît pas.
            DisplayActions(
                value = CopyableValue.of(level),
                tag = DisplayTags.stackLevelActions(depth),
                modifier = Modifier.fillMaxWidth(),
                onCopied = onCopied,
            ) {
                StackRow(
                    level = level,
                    depth = depth,
                    rank = firstLevel + depth,
                    showDecimal = showDecimal,
                )
            }
        }
    }
}

/** Un niveau de pile : son rang, sa valeur décimale à gauche, ses glyphes à droite. */
@Composable
private fun StackRow(level: StackLevel, depth: Int, rank: Int, showDecimal: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // « Pile, niveau 1 : BuZo » — le rang et les noms Shadok. Les formes ne se prononcent
    // pas, et répéter le décimal ferait doublon avec la grande ligne.
    val description = stringResource(R.string.display_stack_level, rank, level.labels)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Même précaution que sur les touches : clearAndSetSemantics efface la
            // sémantique des descendants, donc le testTag doit être posé dans le bloc.
            .clearAndSetSemantics {
                contentDescription = description
                testTag = DisplayTags.stackLevel(depth)
            },
        horizontalArrangement = Arrangement.spacedBy(VALUE_COLUMN_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelMarker(rank)
        // Moitiés égales, ici comme sur la grande ligne : les décimales s'alignent en
        // colonne du fond de pile jusqu'au calcul en cours, et les glyphes de même à droite.
        // Décimal masqué, les glyphes prennent toute la largeur.
        if (showDecimal) {
            Text(
                text = level.decimalWithMarker(),
                style = DisplayTypography.decimal,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            )
        }
        ShadokGlyphText(
            expression = level.glyphs,
            semanticsLabel = level.labels,
            style = DisplayTypography.labels,
            color = MaterialTheme.colorScheme.onSurface,
            operatorColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .weight(1f)
                // Une valeur empilée se lit par la tête : ce sont ses rangs les plus forts
                // qui disent ce qu'elle vaut. Défiler jusqu'à sa queue, comme le fait une
                // frappe en cours, ne montrerait d'un grand nombre que ses unités.
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Le rang d'une valeur — « 1 : » pour le sommet — ou rien du tout pour une frappe.
 *
 * C'est l'autre moitié de ce qu'ENTER rend visible : le nombre franchit le séparateur *et*
 * reçoit un rang. Une valeur sans rang n'est pas dans la pile ; elle est encore sous le
 * doigt, et ⌫ l'effacerait chiffre à chiffre au lieu de la dépiler.
 */
@Composable
private fun LevelMarker(level: Int?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(LEVEL_COLUMN_WIDTH)) {
        if (level != null) {
            Text(
                text = stringResource(R.string.display_stack_rank, level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Le décimal d'un niveau, précédé de `≈` s'il est tronqué.
 *
 * Contrairement à la grande ligne, le marqueur est ici dans le texte : une ligne de pile est
 * courte, il ne risque pas de sortir de l'écran.
 */
private fun StackLevel.decimalWithMarker(): String =
    if (decimalApproximate) "${ShadokFormatter.APPROXIMATION}$decimal" else decimal
