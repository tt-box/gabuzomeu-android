package eu.ttbox.gabuzomeu.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphText
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphs

// Les deux tables de l'écran d'aide, séparées de la prose parce qu'elles ne s'écrivent pas :
// elles se **calculent**.
//
// Une table recopiée à la main finirait par contredire le moteur ; celles-ci ne peuvent pas. Les
// chiffres viennent de ShadokDigit, les nombres de ShadokConverter et ShadokFormatter, et les
// dessins des mêmes tracés que la calculatrice. Il n'y a donc rien à maintenir en parallèle, et
// rien qui puisse mentir sur ce que l'application fera.

/** Un chiffre : son dessin, son nom, sa valeur. */
@Composable
internal fun DigitRow(digit: ShadokDigit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ShadokGlyphs.of(digit),
            // La ligne entière s'annonce par le nom : le dessin n'a rien à ajouter.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(GLYPH_SIZE),
        )
        Text(
            text = digit.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(LABEL_WIDTH.toDpWidth()),
        )
        Text(
            text = stringResource(R.string.help_digit_value, digit.value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les premiers entiers, dans les quatre écritures.
 *
 * Elle montre les glyphes eux-mêmes et pas seulement les noms : c'est `◿⅃` qu'on cherche à
 * savoir lire, et la table était jusqu'ici la seule partie de la leçon à ne pas en montrer.
 */
@Composable
internal fun NumberTable() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Sans en-tête, les deux colonnes de chiffres — « 6 » et « 12 » — se lisent comme un
        // seul nombre, ou comme une erreur.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderCell(
                text = stringResource(R.string.mode_decimal),
                align = TextAlign.End,
                modifier = Modifier.width(CELL_DECIMAL.toDpWidth()),
            )
            HeaderCell(
                text = stringResource(R.string.help_table_glyphs),
                align = TextAlign.End,
                modifier = Modifier.width(CELL_GLYPHS.toDpWidth()),
            )
            HeaderCell(
                text = stringResource(R.string.help_table_name),
                align = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            HeaderCell(
                text = stringResource(R.string.help_table_base4),
                align = TextAlign.End,
                modifier = Modifier.width(CELL_BASE4.toDpWidth()),
            )
        }

        for (value in 0..TABLE_LAST) {
            NumberRow(value)
        }
    }
}

/** Une ligne de la table : le même nombre dans les quatre écritures. */
@Composable
private fun NumberRow(value: Int) {
    val base4 = ShadokConverter.toBase4(Rational.of(value))
    val labels = ShadokFormatter.format(base4, ShadokNotation.LABELS)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell("$value", TextAlign.End, Modifier.width(CELL_DECIMAL.toDpWidth()))
        ShadokGlyphText(
            expression = ShadokFormatter.format(base4, ShadokNotation.GLYPHS),
            // Les formes ne se prononcent pas : TalkBack lit les noms, comme partout ailleurs.
            semanticsLabel = labels,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            operatorColor = MaterialTheme.colorScheme.tertiary,
            // Alignés à droite par construction : les unités tombent donc les unes sous les
            // autres d'une ligne à l'autre, ce qui fait voir les rangs.
            modifier = Modifier.width(CELL_GLYPHS.toDpWidth()),
        )
        Text(
            text = labels,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TableCell(
            text = ShadokFormatter.format(base4, ShadokNotation.BASE4),
            align = TextAlign.End,
            modifier = Modifier.width(CELL_BASE4.toDpWidth()),
        )
    }
}

/** L'intitulé d'une colonne, aligné comme le contenu qu'il coiffe. */
@Composable
private fun HeaderCell(text: String, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

/** Une cellule de chiffres. Largeur commune et chasse fixe : sans quoi rien ne s'aligne. */
@Composable
private fun TableCell(text: String, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

/** Jusqu'à 16 : la première grande poubelle, donc la table est complète. */
private const val TABLE_LAST = 16

private val GLYPH_SIZE = 28.dp

// Toutes les largeurs de colonne sont en **sp**, pas en dp : elles portent du texte, et ce texte
// grandit avec la police système. En dp, à 200 %, « 16 » perdait son premier glyphe et les
// noms se coupaient. Converties au dernier moment par [toDpWidth], elles suivent la même échelle
// que ce qu'elles contiennent — et les colonnes restent alignées d'une ligne à l'autre.
private val LABEL_WIDTH = 48.sp

// Les colonnes de chiffres sont taillées sur leur **en-tête**, pas sur leur contenu : « 16 »
// tient dans 20 dp, mais « Décimal » se coupait en « Déci / mal » au-dessus. Une colonne dont
// le titre se casse en deux se lit comme deux colonnes.
private val CELL_DECIMAL = 64.sp
private val CELL_GLYPHS = 64.sp
private val CELL_BASE4 = 46.sp

/** Une largeur en sp, rendue en dp à l'échelle de police courante. */
@Composable
private fun TextUnit.toDpWidth(): Dp = with(LocalDensity.current) { toDp() }
