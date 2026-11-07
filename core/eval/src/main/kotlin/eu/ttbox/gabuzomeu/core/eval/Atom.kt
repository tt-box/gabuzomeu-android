package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Base4Number
import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokParser

/** La notation dans laquelle un nombre a été saisi. */
enum class NumberNotation {
    /** Chiffres 0-9. */
    DECIMAL,

    /** Glyphes Shadok ◯ _ ⅃ ◿. */
    SHADOK,
}

/**
 * Élément indivisible d'une expression.
 *
 * Un nombre conserve **la notation dans laquelle il a été tapé** ainsi que ses chiffres
 * tels que saisis. C'est ce qui permet à la fois d'ajouter un chiffre à la fin dans les
 * deux modes de saisie, et de restituer exactement ce que l'utilisateur a écrit — sans
 * l'aller-retour chaîne↔chaîne du code d'origine, qui perdait de l'information
 * (`encode` puis `decode` ne redonnait pas l'entrée dès qu'il y avait une décimale).
 *
 * Note utile : tout nombre à développement fini en base 4 a aussi un développement fini
 * en base 10, puisque 4 = 2² et que 2 divise 10. Une saisie Shadok est donc toujours
 * représentable exactement en décimal. L'inverse est faux — d'où le marqueur
 * d'approximation dans l'autre sens.
 */
sealed interface Atom {

    data class Number(
        val notation: NumberNotation,
        /** Chiffres tels que saisis, dans [notation]. Peut finir par un séparateur. */
        val digits: String,
        /**
         * La valeur exacte, quand [digits] n'en est qu'une écriture approchée : un décimal
         * 0.1 basculé en Shadok (pas d'écriture finie en base 4), ou un tiers repris d'un
         * résultat. `null` pour une frappe ordinaire, dont les chiffres *sont* la valeur.
         *
         * Sans elle, basculer de notation réécrivait le nombre tronqué à 16 chiffres et en
         * changeait la valeur : 0.1 → Shadok → décimal revenait en 0.0999999998…
         * Toute retouche des chiffres (`appendDigit`, ⌫…) l'abandonne : ce que l'utilisateur
         * modifie, ce sont les chiffres qu'il voit.
         */
        val exact: Rational? = null,
    ) : Atom {

        /** `true` si un séparateur décimal a déjà été saisi dans ce nombre. */
        val hasSeparator: Boolean get() = digits.contains(ShadokFormatter.SEPARATOR)

        /** Valeur exacte. Un séparateur final en cours de frappe est ignoré. */
        fun value(): Rational {
            if (exact != null) return exact
            val sanitized = digits.trimEnd(ShadokFormatter.SEPARATOR)
            if (sanitized.isEmpty()) return Rational.ZERO
            return when (notation) {
                NumberNotation.DECIMAL -> Rational.ofDecimal(sanitized)

                NumberNotation.SHADOK ->
                    ShadokParser.parseGlyphsOrNull(sanitized)
                        ?.let { ShadokConverter.toRational(it) }
                        ?: Rational.ZERO
            }
        }

        /** Écriture Shadok de ce nombre, avec son éventuel marqueur d'approximation. */
        fun toBase4(): Base4Number = ShadokConverter.toBase4(value())
    }

    data class Op(val operator: Operator) : Atom

    data object LeftParen : Atom

    data object RightParen : Atom
}
