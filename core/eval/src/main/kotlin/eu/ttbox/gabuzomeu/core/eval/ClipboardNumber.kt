package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Base4Number
import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import eu.ttbox.gabuzomeu.core.shadok.ShadokParser

/**
 * Un nombre collé, réduit à ce que le pavé sait taper.
 *
 * @property keys les chiffres et le séparateur, dans la notation active — donc des caractères
 *   que `ExpressionBuffer.appendDigit` accepte. Jamais de signe : voir [negative].
 * @property negative le signe, porté à part. `Atom.Number.digits` n'en contient pas, et en
 *   infixe un négatif est un `Atom.Op(MINUS)`, ce qui n'a pas de sens dans une suite de
 *   chiffres. C'est à l'appelant de l'appliquer avec les moyens de son mode.
 * @property value la valeur exacte lue, signe compris. Quand [approximate] est vrai, [keys]
 *   n'en est qu'une écriture arrondie : l'appelant peut la porter dans
 *   `Atom.Number.exact` plutôt que de laisser la valeur glisser.
 * @property approximate `true` si [keys] ne redonne pas exactement [value] — 0.7 décimal collé
 *   dans le pavé Shadok, par exemple. Avant, cette troncature passait en silence.
 */
data class PastedNumber(
    val keys: String,
    val negative: Boolean,
    val value: Rational = Rational.ZERO,
    val approximate: Boolean = false,
)

/**
 * La lecture d'un nombre venu du presse-papiers.
 *
 * Le pendant de la copie : l'application écrit une valeur dans quatre écritures, elle doit
 * savoir les relire toutes. Fonction pure, sans Android — c'est le presse-papiers qui est une
 * affaire de plateforme, pas la lecture de son contenu.
 */
object ClipboardNumber {

    /** Un décimal ordinaire : ni exposant, ni espace, ni séparateur de milliers. */
    private val DECIMAL_BODY = Regex("""\d*\.?\d*""")

    private val BASE4_BODY = Regex("""[0-3]*\.?[0-3]*""")

    /**
     * @param notation celle du pavé actif. Elle tranche le **seul cas ambigu** : `12` est un
     *   nombre valide en décimal comme en base 4. En Shadok il vaut donc 6, en décimal 12.
     *   Ce choix se défend par où va le collage — il entre dans le pavé sur lequel on tape —
     *   et il rend fidèle l'aller-retour « copier la base 4, puis coller ».
     * @return `null` si [text] n'est pas un nombre. L'appelant s'en sert pour griser
     *   « Coller » plutôt que d'échouer en silence une fois le doigt levé.
     */
    fun parseOrNull(text: String, notation: NumberNotation): PastedNumber? {
        val trimmed = text.trim()
        val value = valueOrNull(trimmed, notation) ?: return null

        val keys = keysOf(value.abs(), notation)
        return PastedNumber(
            keys = keys,
            negative = value.signum() < 0,
            value = value,
            approximate = Atom.Number(notation, keys).value() != value.abs(),
        )
    }

    /** Essaie les écritures de la moins ambiguë à la plus ambiguë. */
    private fun valueOrNull(text: String, notation: NumberNotation): Rational? {
        ShadokParser.parseGlyphsOrNull(text)?.let { return ShadokConverter.toRational(it) }
        ShadokParser.parseLabelsOrNull(text)?.let { return ShadokConverter.toRational(it) }
        return digitsOrNull(text, notation)
    }

    private fun digitsOrNull(text: String, notation: NumberNotation): Rational? {
        val negative = text.startsWith(ShadokFormatter.MINUS) || text.startsWith('-')
        // Le signe est retiré à la main : la calculatrice copie le vrai U+2212, que
        // BigDecimal refuse. Le retirer ici évite de traiter ce cas deux fois.
        val body = if (negative) text.substring(1) else text
        if (body.none { it.isDigit() }) return null

        val magnitude = when {
            // La base 4 ne s'applique qu'au pavé Shadok, et qu'à des chiffres qui existent
            // en base 4 : « 12 » vaut 6 ici, « 42 » n'est pas lisible et reste décimal.
            notation == NumberNotation.SHADOK && BASE4_BODY.matches(body) -> base4Of(body)

            // Volontairement plus strict que BigDecimal, qui accepterait « 1e9 » — et donc
            // « 1e999999999 », dont la mise à l'échelle épuiserait la mémoire sur un simple
            // collage. Un nombre collé est une suite de chiffres, éventuellement pointée.
            DECIMAL_BODY.matches(body) -> Rational.ofDecimal(body)

            else -> return null
        }
        return if (negative) -magnitude else magnitude
    }

    /**
     * Lit un corps déjà validé comme base 4 : chaque caractère est un chiffre de 0 à 3.
     *
     * `ShadokDigit.of` lève hors de cette plage — c'est [BASE4_BODY] qui garantit qu'on n'y
     * arrive jamais, et non un `null` silencieux qui masquerait une erreur de validation.
     */
    private fun base4Of(body: String): Rational {
        val (integerPart, fractionPart) = body.split(ShadokFormatter.SEPARATOR)
            .let { parts -> parts.first() to parts.drop(1).joinToString(separator = "") }
        val digitsOf = { part: String -> part.map { ShadokDigit.of(it - '0') } }

        return ShadokConverter.toRational(
            Base4Number(
                negative = false,
                // « .⅃ » vaut « ◯.⅃ » : la partie entière n'est jamais vide.
                integerDigits = digitsOf(integerPart).ifEmpty { listOf(ShadokDigit.GA) },
                fractionDigits = digitsOf(fractionPart),
            ),
        )
    }

    /**
     * Les chiffres à taper pour obtenir [magnitude] sur le pavé actif.
     *
     * En Shadok, ce sont des **glyphes** : `isValidDigit` n'accepte rien d'autre dans cette
     * notation — jamais un chiffre base 4, même si c'est ainsi que la valeur a été collée.
     */
    private fun keysOf(magnitude: Rational, notation: NumberNotation): String = when (notation) {
        NumberNotation.DECIMAL -> magnitude.toDecimalString()

        NumberNotation.SHADOK -> ShadokFormatter.format(
            number = ShadokConverter.toBase4(magnitude),
            notation = ShadokNotation.GLYPHS,
            // Le « ≈ » n'est pas un caractère que le pavé sait taper : une valeur sans
            // écriture finie en base 4 est arrondie, et `PastedNumber.approximate` le dit.
            markApproximation = false,
        )
    }
}
