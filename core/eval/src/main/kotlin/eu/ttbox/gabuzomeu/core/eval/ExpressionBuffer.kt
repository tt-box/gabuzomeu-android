package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation

/**
 * Les quatre écritures d'une expression.
 *
 * Trois sont affichées simultanément ; [SHADOK_BASE4] ne l'est pas — elle sert à copier une
 * valeur sous une forme lisible partout, y compris là où les glyphes s'afficheraient en tofu.
 */
enum class ExpressionDisplay {
    DECIMAL,
    SHADOK_GLYPHS,
    SHADOK_LABELS,

    /** Chiffres bruts en base 4 : `12` pour 6. Copiable et relisible sans police spéciale. */
    SHADOK_BASE4,
}

/**
 * Une expression rendue, avec l'information de fidélité.
 *
 * @property approximate `true` si au moins un nombre a dû être tronqué pour être écrit
 *   dans cette notation. L'interface préfixe alors l'affichage d'un `≈` plutôt que de
 *   présenter des chiffres faux comme exacts.
 */
data class Rendered(val text: String, val approximate: Boolean)

/**
 * L'expression en cours de saisie : **la source de vérité unique**.
 *
 * Les trois affichages (décimal, glyphes, noms) en sont des projections pures. Le code
 * d'origine faisait l'inverse — il synchronisait trois champs de texte entre eux par
 * `encode`/`decode` successifs ([ui/CalculatorConverterDisplay.java]), ce qui perdait
 * de l'information à chaque aller-retour.
 *
 * Immuable : chaque opération renvoie un nouveau tampon, ce qui en fait un état de
 * `StateFlow` naturel et rend chaque règle de saisie testable isolément.
 *
 * Tous les nombres du tampon partagent [notation], celle du pavé actif. Changer de mode
 * se fait via [withNotation], qui convertit l'expression d'un bloc — jamais au milieu
 * d'un nombre.
 */
data class ExpressionBuffer(
    val notation: NumberNotation = NumberNotation.DECIMAL,
    val atoms: List<Atom> = emptyList(),
) {

    val isEmpty: Boolean get() = atoms.isEmpty()

    private val lastAtom: Atom? get() = atoms.lastOrNull()

    private val openParenCount: Int
        get() = atoms.count { it is Atom.LeftParen } - atoms.count { it is Atom.RightParen }

    // ------------------------------------------------------------ saisie

    /**
     * Ajoute un chiffre. Le caractère doit appartenir à [notation] : '0'-'9' en décimal,
     * un glyphe Shadok sinon. Un chiffre invalide laisse le tampon inchangé.
     */
    fun appendDigit(digit: Char): ExpressionBuffer {
        if (!isValidDigit(digit)) return this
        // Pas de multiplication implicite : « (2)3 » n'a pas de sens.
        if (lastAtom is Atom.RightParen) return this

        val last = lastAtom
        return if (last is Atom.Number) {
            replaceLast(last.withDigits(last.digits + digit))
        } else {
            append(Atom.Number(notation, digit.toString()))
        }
    }

    /**
     * Ajoute le séparateur décimal.
     *
     * **Règle 1** — pas de second séparateur dans un même nombre
     * (`CalculatorEditable.java:64-73`).
     */
    fun appendSeparator(): ExpressionBuffer {
        if (lastAtom is Atom.RightParen) return this

        val last = lastAtom
        return if (last is Atom.Number) {
            if (last.hasSeparator) {
                this
            } else {
                replaceLast(last.withDigits(last.digits + ShadokFormatter.SEPARATOR))
            }
        } else {
            // « .5 » s'écrit « 0.5 » : on matérialise le zéro de tête.
            append(Atom.Number(notation, "$zeroDigit${ShadokFormatter.SEPARATOR}"))
        }
    }

    /**
     * Ajoute un opérateur.
     *
     * **Règle 2** — pas deux `−` successifs (`CalculatorEditable.java:77-80`).
     *
     * **Règle 3** — reprise fidèle de la boucle de `CalculatorEditable.java:82-89` :
     * ```java
     * while (isOperator(prevChar) && (text != MINUS || prevChar == '+'))
     * ```
     * Pour un `−`, la condition se réduit à `prevChar == '+'`. Autrement dit :
     * - un opérateur autre que `−` écrase **tous** les opérateurs qui traînent ;
     * - un `−` n'écrase qu'un `+` ; après `×` ou `÷` il est **ajouté** et devient un
     *   moins unaire, ce qui rend `2×−3` saisissable et égal à −6.
     *
     * **Règle 4** — pas d'opérateur en tête, sauf `−` (`CalculatorEditable.java:91-94`).
     */
    fun appendOperator(operator: Operator): ExpressionBuffer {
        val last = lastAtom

        // Règle 4, y compris juste après une parenthèse ouvrante.
        if (last == null || last is Atom.LeftParen) {
            return if (operator == Operator.MINUS) append(Atom.Op(operator)) else this
        }

        if (last is Atom.Op) {
            // Règle 2
            if (operator == Operator.MINUS && last.operator == Operator.MINUS) return this
            // Règle 3 — le moins unaire après × ou ÷ s'ajoute au lieu d'écraser.
            if (operator == Operator.MINUS && last.operator != Operator.PLUS) {
                return append(Atom.Op(operator))
            }
            // Règle 3 — sinon l'opérateur écrase toute la traîne d'opérateurs.
            val trimmed = atoms.dropLastWhile { it is Atom.Op }
            val precedingAllowsOperator =
                trimmed.lastOrNull()?.let { it !is Atom.LeftParen } == true
            // Règle 4 de nouveau : écraser peut ramener en tête d'expression.
            if (!precedingAllowsOperator && operator != Operator.MINUS) return this
            return copy(atoms = trimmed + Atom.Op(operator))
        }

        return append(Atom.Op(operator))
    }

    fun appendLeftParen(): ExpressionBuffer {
        // Pas de multiplication implicite : « 2( » est refusé.
        if (lastAtom is Atom.Number || lastAtom is Atom.RightParen) return this
        return append(Atom.LeftParen)
    }

    fun appendRightParen(): ExpressionBuffer {
        if (openParenCount <= 0) return this
        // Fermer juste après « ( » ou un opérateur donnerait une expression vide.
        if (lastAtom !is Atom.Number && lastAtom !is Atom.RightParen) return this
        return append(Atom.RightParen)
    }

    /** Supprime le dernier caractère saisi — un chiffre, ou l'atome entier. */
    fun deleteLast(): ExpressionBuffer {
        val last = lastAtom ?: return this
        return if (last is Atom.Number && last.digits.length > 1) {
            replaceLast(last.withDigits(last.digits.dropLast(1)))
        } else {
            copy(atoms = atoms.dropLast(1))
        }
    }

    fun clear(): ExpressionBuffer = copy(atoms = emptyList())

    /**
     * Bascule le mode de saisie, en réécrivant tous les nombres d'un bloc.
     *
     * **La valeur ne change jamais** : seule l'écriture suit la notation. Quand elle est
     * approchée (0.1 décimal n'a pas de développement fini en base 4), le nombre garde sa
     * valeur exacte dans [Atom.Number.exact], et `render(...).approximate` le signale.
     */
    fun withNotation(target: NumberNotation): ExpressionBuffer {
        if (target == notation) return this
        val converted = atoms.map { atom ->
            if (atom !is Atom.Number) {
                atom
            } else {
                val value = atom.value()
                val digits = atom.digitsIn(target)
                // Les chiffres réécrits suffisent quand ils redonnent la valeur ; sinon, on
                // la porte à côté plutôt que de la laisser glisser à chaque bascule.
                val exact = value.takeIf { Atom.Number(target, digits).value() != it }
                Atom.Number(target, digits, exact)
            }
        }
        return ExpressionBuffer(notation = target, atoms = converted)
    }

    // ---------------------------------------------------------- affichage

    /**
     * L'expression sous une forme que [replay] sait relire — la clé de persistance.
     *
     * On stocke la frappe, pas une structure sérialisée : la restauration repasse donc
     * par les règles de saisie et ne peut produire qu'un tampon valide.
     */
    fun replayKeys(): String = when (notation) {
        NumberNotation.DECIMAL -> render(ExpressionDisplay.DECIMAL).text
        NumberNotation.SHADOK -> render(ExpressionDisplay.SHADOK_GLYPHS).text
    }

    fun render(display: ExpressionDisplay): Rendered {
        var approximate = false
        val text = buildString {
            atoms.forEach { atom ->
                when (atom) {
                    is Atom.Number -> {
                        if (atom.isApproximateIn(display)) approximate = true
                        append(atom.render(display))
                    }

                    is Atom.Op -> append(atom.operator.symbol)

                    Atom.LeftParen -> append('(')

                    Atom.RightParen -> append(')')
                }
            }
        }
        return Rendered(text, approximate)
    }

    // ------------------------------------------------------------ interne

    private val zeroDigit: Char
        get() = when (notation) {
            NumberNotation.DECIMAL -> '0'
            NumberNotation.SHADOK -> ShadokDigit.GA.glyph
        }

    private fun isValidDigit(digit: Char): Boolean = when (notation) {
        NumberNotation.DECIMAL -> digit in '0'..'9'
        NumberNotation.SHADOK -> ShadokDigit.isGlyph(digit)
    }

    private fun append(atom: Atom): ExpressionBuffer = copy(atoms = atoms + atom)

    private fun replaceLast(atom: Atom): ExpressionBuffer = copy(atoms = atoms.dropLast(1) + atom)

    companion object {

        /**
         * Rejoue une saisie caractère par caractère.
         *
         * Sert à restaurer un état persisté : comme la restauration passe par les mêmes
         * `append*` que la frappe, **aucun tampon invalide ne peut être reconstruit**
         * depuis des données stockées. Le code d'origine, lui, désérialisait un format
         * binaire maison (`Persist.java`) dont les contrôles de version étaient inversés.
         */
        fun replay(
            keys: String,
            notation: NumberNotation = NumberNotation.DECIMAL,
        ): ExpressionBuffer {
            var buffer = ExpressionBuffer(notation)
            keys.forEach { key ->
                buffer = when {
                    key == ShadokFormatter.SEPARATOR -> buffer.appendSeparator()

                    key == '(' -> buffer.appendLeftParen()

                    key == ')' -> buffer.appendRightParen()

                    Operator.isOperator(key) ->
                        buffer.appendOperator(Operator.ofSymbolOrNull(key)!!)

                    else -> buffer.appendDigit(key)
                }
            }
            return buffer
        }

        /** Chiffres de ce nombre réécrits dans [target], en préservant la frappe en cours. */
        private fun Atom.Number.digitsIn(target: NumberNotation): String {
            if (target == notation) return digits
            val trailingSeparator = digits.endsWith(ShadokFormatter.SEPARATOR)
            val body = when (target) {
                NumberNotation.DECIMAL -> value().toDecimalString()

                NumberNotation.SHADOK -> ShadokFormatter.format(
                    number = toBase4(),
                    notation = ShadokNotation.GLYPHS,
                    markApproximation = false,
                )
            }
            return if (trailingSeparator && !body.contains(ShadokFormatter.SEPARATOR)) {
                body + ShadokFormatter.SEPARATOR
            } else {
                body
            }
        }

        /** Une retouche des chiffres : la valeur exacte éventuelle ne leur correspond plus. */
        private fun Atom.Number.withDigits(newDigits: String): Atom.Number =
            copy(digits = newDigits, exact = null)

        /**
         * Jugé sur la **valeur**, pas sur la notation de saisie : un nombre qui porte une
         * valeur exacte peut être approché dans sa propre notation (0.1 basculé en Shadok,
         * un tiers repris d'un résultat).
         */
        private fun Atom.Number.isApproximateIn(display: ExpressionDisplay): Boolean =
            when (display) {
                // Toute frappe — décimale, ou Shadok puisque 4 = 2² — s'écrit exactement en
                // décimal ; seule une valeur exacte comme un tiers peut ne pas l'être.
                ExpressionDisplay.DECIMAL -> !value().hasFiniteDecimal

                // Les trois écritures Shadok décrivent les mêmes chiffres : elles sont
                // approchées ensemble ou pas du tout.
                ExpressionDisplay.SHADOK_GLYPHS,
                ExpressionDisplay.SHADOK_LABELS,
                ExpressionDisplay.SHADOK_BASE4,
                // Une frappe Shadok est rendue verbatim, donc exacte, quelle que soit sa
                // longueur ; seule une valeur portée à côté de ses chiffres peut ne pas l'être.
                -> (notation != NumberNotation.SHADOK || exact != null) && toBase4().approximate
            }

        private fun Atom.Number.render(display: ExpressionDisplay): String = when (display) {
            ExpressionDisplay.DECIMAL -> renderDecimal()
            ExpressionDisplay.SHADOK_GLYPHS -> renderShadok(ShadokNotation.GLYPHS)
            ExpressionDisplay.SHADOK_LABELS -> renderShadok(ShadokNotation.LABELS)
            ExpressionDisplay.SHADOK_BASE4 -> renderShadok(ShadokNotation.BASE4)
        }

        private fun Atom.Number.renderDecimal(): String = when (notation) {
            // Verbatim : préserve « 1.50 » ou un séparateur en cours de frappe.
            NumberNotation.DECIMAL -> digits

            NumberNotation.SHADOK -> value().toDecimalString()
        }

        private fun Atom.Number.renderShadok(shadokNotation: ShadokNotation): String {
            if (notation == NumberNotation.SHADOK) {
                // Verbatim, glyphe par glyphe, pour préserver la frappe en cours.
                return digits.map { char ->
                    // Le séparateur décimal et le reste passent tels quels ; seuls les
                    // glyphes se réécrivent. Le `when` sur la notation est exhaustif : une
                    // cinquième écriture ne pourrait pas être oubliée ici.
                    when (val digit = ShadokDigit.ofGlyphOrNull(char)) {
                        null -> char.toString()

                        else -> when (shadokNotation) {
                            ShadokNotation.GLYPHS -> digit.glyph.toString()
                            ShadokNotation.LABELS -> digit.label
                            ShadokNotation.BASE4 -> digit.base4Char.toString()
                        }
                    }
                }.joinToString(separator = "")
            }
            return ShadokFormatter.format(
                number = toBase4(),
                notation = shadokNotation,
                markApproximation = false,
            )
        }
    }
}
