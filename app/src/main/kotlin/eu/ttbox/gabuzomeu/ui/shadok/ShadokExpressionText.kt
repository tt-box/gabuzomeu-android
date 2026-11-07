package eu.ttbox.gabuzomeu.ui.shadok

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit

/**
 * Caractères qui séparent les nombres dans une expression : opérateurs et parenthèses.
 *
 * Ils reçoivent une couleur et un espacement distincts. Sans cela, `BuZo×Zo` se lit
 * comme un seul bloc — le problème est particulièrement aigu sur la ligne des noms, où
 * lettres et symboles ont le même poids visuel.
 */
private const val SEPARATOR_CHARS = "+−×÷()-*/"

/** Largeur d'un glyphe rapportée à sa hauteur : resserre les chiffres d'un même nombre. */
private const val GLYPH_ADVANCE_RATIO = 0.78f

/**
 * Espace U+2005 (quart de cadratin) autour des opérateurs.
 *
 * Une espace fine U+2009 ne suffisait pas. `−` (U+2212) et le glyphe `Bu` (`_`, U+005F) sont
 * deux barres horizontales de longueur voisine : collées à un chiffre, elles se lisaient comme
 * une seule suite. Le problème valait aussi pour le signe d'un nombre négatif, dont le `−`
 * de tête pouvait passer pour un `Bu`.
 */
private const val OPERATOR_SPACE = ' '

/**
 * Les opérateurs sont écrits **plus grand** que les chiffres qui les entourent.
 *
 * C'est le levier décisif contre la confusion `−`/`Bu` : la couleur et la graisse, déjà en
 * place, ne changeaient ni la longueur ni l'épaisseur de la barre. Exprimée en `em`, la taille
 * reste relative à celle de la ligne — l'écart tient donc à toutes les tailles de police
 * système, sans calcul à refaire.
 */
private val OPERATOR_SIZE = 1.3f.em

/**
 * Affiche une expression en **glyphes** Shadok, opérateurs compris.
 *
 * Les chiffres sont des dessins vectoriels, les opérateurs du texte : le mélange se fait
 * par `InlineTextContent`, ce qui laisse le moteur de texte gérer alignement, retour à
 * la ligne et mise à l'échelle avec la taille de police du système.
 *
 * @param semanticsLabel ce que doit lire TalkBack — les **noms** Shadok, pas les formes.
 *   C'est ici que le bug d'accessibilité du projet d'origine devient impossible : les
 *   descriptions Zo/Meu y étaient inversées dans `res/layout-port/shadok_pad.xml:32-42`
 *   parce qu'elles étaient recopiées à la main dans le XML, alors qu'elles dérivent
 *   désormais toujours de [ShadokDigit.label].
 */
@Composable
fun ShadokGlyphText(
    expression: String,
    semanticsLabel: String,
    style: TextStyle,
    color: Color,
    operatorColor: Color,
    modifier: Modifier = Modifier,
) {
    val glyphSize = style.fontSize

    val inlineContent = remember(glyphSize, color) {
        ShadokDigit.entries.associate { digit ->
            inlineIdOf(digit) to InlineTextContent(
                placeholder = Placeholder(
                    // Avance plus étroite que la hauteur : les chiffres d'un même nombre
                    // se serrent, ce qui fait ressortir par contraste les espaces fines
                    // autour des opérateurs. Un carré parfait donnait des écarts
                    // intra-nombre aussi larges que les séparateurs, et « _⅃ » ne se
                    // lisait plus comme un seul nombre.
                    width = glyphSize * GLYPH_ADVANCE_RATIO,
                    height = glyphSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Icon(
                    imageVector = ShadokGlyphs.of(digit),
                    // La description est portée par le conteneur entier.
                    contentDescription = null,
                    tint = color,
                    // aspectRatio : le tracé reste carré, sans déformation.
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
        }
    }

    val annotated = remember(expression, operatorColor) {
        buildAnnotatedString {
            tokenize(expression).forEach { token ->
                when (token) {
                    is ExpressionToken.Glyph ->
                        // Le second argument est le texte de repli si le contenu inline
                        // n'est pas résolu — on y met le glyphe lui-même.
                        appendInlineContent(inlineIdOf(token.digit), token.digit.glyph.toString())

                    is ExpressionToken.Separator -> appendSeparator(token.character, operatorColor)

                    is ExpressionToken.Other -> append(token.character)
                }
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = style,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = modifier.clearAndSetSemantics { contentDescription = semanticsLabel },
    )
}

/**
 * Affiche une expression en **noms** Shadok (`BuZo×Zo`), opérateurs mis en valeur.
 */
@Composable
fun ShadokLabelText(
    expression: String,
    style: TextStyle,
    color: Color,
    operatorColor: Color,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(expression, operatorColor) {
        buildAnnotatedString {
            expression.forEach { character ->
                if (character in SEPARATOR_CHARS) {
                    appendSeparator(character, operatorColor)
                } else {
                    append(character)
                }
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

/** Un caractère d'une expression, classé selon la façon dont il s'affiche. */
internal sealed interface ExpressionToken {
    /** Un chiffre Shadok, dessiné. */
    data class Glyph(val digit: ShadokDigit) : ExpressionToken

    /** Un opérateur ou une parenthèse, mis en valeur. */
    data class Separator(val character: Char) : ExpressionToken

    /** Tout le reste, écrit tel quel. */
    data class Other(val character: Char) : ExpressionToken
}

/** Découpe une expression caractère par caractère : glyphe, séparateur, ou autre. */
internal fun tokenize(expression: String): List<ExpressionToken> = expression.map { character ->
    val digit = ShadokDigit.ofGlyphOrNull(character)
    when {
        digit != null -> ExpressionToken.Glyph(digit)
        character in SEPARATOR_CHARS -> ExpressionToken.Separator(character)
        else -> ExpressionToken.Other(character)
    }
}

/** Un opérateur, coloré et entouré d'espaces fines. */
private fun AnnotatedString.Builder.appendSeparator(character: Char, operatorColor: Color) {
    append(OPERATOR_SPACE)
    withStyle(
        SpanStyle(
            color = operatorColor,
            fontWeight = FontWeight.Bold,
            fontSize = OPERATOR_SIZE,
        ),
    ) {
        append(character)
    }
    append(OPERATOR_SPACE)
}

private fun inlineIdOf(digit: ShadokDigit): String = "shadok-${digit.name}"
