package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation

/**
 * Le résultat d'une frappe en mode Simple : le nouvel état, et l'erreur éventuelle.
 *
 * Même contrat que [RpnOutcome] : quand [error] n'est pas nul, [session] est **l'état
 * d'avant, intact**. Une division par zéro ne fait perdre ni l'accumulateur ni la frappe.
 */
data class SimpleOutcome(val session: SimpleSession, val error: EvalError? = null)

/**
 * La calculatrice **à exécution immédiate** : celle de la machine à calculer des Shadoks.
 *
 * Un accumulateur, une opération en attente, une frappe en cours — rien d'autre. Chaque
 * opérateur résout l'opération précédente au lieu de l'accumuler, donc **il n'y a pas de
 * priorité** : `Bu + Zo × Meu =` vaut 9, là où le mode classique, qui parse une expression
 * entière, donne 7. Ce n'est pas une approximation du mode classique, c'est une autre
 * machine — celle qu'on a dans la poche.
 *
 * Comme [RpnSession], la frappe *est* un [ExpressionBuffer] restreint à un seul
 * [Atom.Number] : les règles de saisie — un seul séparateur, le zéro de tête matérialisé, le
 * filtrage des chiffres selon la notation, la conversion décimal↔Shadok en pleine frappe —
 * sont déjà écrites et déjà testées.
 *
 * @property accumulator le résultat courant, en fraction exacte. `null` tant qu'aucun nombre
 *   n'a été validé.
 * @property pending l'opération qui attend son opérande droit. C'est **l'état invisible** du
 *   mode : après `Bu +`, l'afficheur montre toujours `Bu`. L'interface doit donc le rendre
 *   visible, faute de quoi deux états distincts produiraient la même image — le défaut
 *   corrigé pour la NPI en 2.0.0-RC5.
 * @property showingResult l'affichage vient d'un `=` : un chiffre repart alors de zéro.
 * @property entryNegative le signe de la frappe, porté ici et non dans les chiffres —
 *   `Atom.Number.digits` n'en a pas. Il sert au ± ([negate]) et à ce qu'un nombre **collé**
 *   négatif reste négatif, plutôt que de perdre son signe en silence.
 */
data class SimpleSession(
    val entry: ExpressionBuffer = ExpressionBuffer(),
    val accumulator: Rational? = null,
    val pending: Operator? = null,
    val showingResult: Boolean = false,
    val entryNegative: Boolean = false,
) {

    /** La notation de saisie, **lue sur le tampon** et non stockée en double. */
    val notation: NumberNotation get() = entry.notation

    /** Une frappe est en cours. **Dérivé** : un état de moins à tenir synchronisé. */
    val entering: Boolean get() = !entry.isEmpty

    /** Rien n'a été tapé, rien n'est en attente : la machine est au repos. */
    val isPristine: Boolean get() = !entering && accumulator == null && pending == null

    // -------------------------------------------------------------------- saisie

    /**
     * Ajoute un chiffre à la frappe.
     *
     * Après un `=`, le chiffre **repart de zéro** : accumulateur et opération en attente sont
     * oubliés. C'est la règle 5 du mode classique (`CalculatorViewModel.onClassicKey`), et
     * le geste attendu de n'importe quelle calculatrice — on ne prolonge pas un résultat en
     * lui accolant des chiffres.
     */
    fun appendDigit(digit: Char): SimpleSession =
        afterResult().copy(entry = entry.appendDigit(digit))

    fun appendSeparator(): SimpleSession = afterResult().copy(entry = entry.appendSeparator())

    /**
     * Efface le dernier caractère **de la frappe uniquement**.
     *
     * Jamais l'accumulateur : ⌫ corrige ce qu'on est en train de taper, il ne défait pas un
     * calcul. Même partage des rôles que [RpnSession.deleteLast], où seul `x↓` touche la pile.
     */
    fun deleteLast(): SimpleSession {
        val shortened = entry.deleteLast()
        // Un signe qui survivrait à une frappe vidée serait invisible, et resurgirait au
        // chiffre suivant.
        return copy(entry = shortened, entryNegative = entryNegative && !shortened.isEmpty)
    }

    /**
     * Change le signe **de la valeur affichée** : la frappe si elle est en cours, sinon
     * l'accumulateur — `5 = ±` donne −5, et le calcul se poursuit depuis −5. Sans frappe ni
     * accumulateur, l'afficheur est vide : il n'y a rien à signer.
     */
    fun negate(): SimpleSession = when {
        entering -> copy(entryNegative = !entryNegative)
        accumulator != null -> copy(accumulator = -accumulator)
        else -> this
    }

    /** POMPER : la frappe, l'accumulateur et l'opération en attente. Tout. */
    fun clear(): SimpleSession = SimpleSession(entry = entry.clear())

    // ------------------------------------------------------------------- calcul

    /**
     * Un opérateur : résout l'opération en attente, puis retient celle-ci.
     *
     * La résolution n'a lieu que si **un nombre a été tapé depuis** la précédente : deux
     * opérateurs de suite remplacent donc simplement le premier, sans rien recalculer. C'est
     * ce qui permet de corriger un `+` malencontreux par un `×` sans effacer.
     *
     * Avant tout nombre, l'opérande gauche **vaut zéro** — ce que montre l'afficheur d'une
     * machine de poche au repos : `− 3 =` donne −3, `× 3 =` donne 0. Ignorer l'opérateur,
     * comme avant, faisait de `− 3 =` un 3 : un signe perdu en silence.
     */
    fun operator(operator: Operator): SimpleOutcome {
        val base = if (accumulator == null && !entering) copy(accumulator = Rational.ZERO) else this
        val resolved = base.resolve() ?: return SimpleOutcome(this, EvalError.DIVISION_BY_ZERO)
        return SimpleOutcome(resolved.copy(pending = operator, showingResult = false))
    }

    /**
     * POMPER, c'est-à-dire « = » : résout l'opération en attente et fige le résultat.
     *
     * Nommée `evaluate` et non `equals` : une méthode sans paramètre nommée `equals` sur une
     * `data class` cohabiterait avec l'`equals(Any?)` généré, et la confusion à la lecture
     * ne vaut pas la fidélité au nom de la touche.
     *
     * Sans opération en attente, la frappe est simplement validée. **Un second `=` ne répète
     * pas la dernière opération** : c'est l'usage de certaines machines de poche, mais il
     * demande de mémoriser l'opérande droit — un état invisible de plus dans un mode qui
     * s'appelle Simple.
     */
    fun evaluate(): SimpleOutcome {
        val resolved = resolve() ?: return SimpleOutcome(this, EvalError.DIVISION_BY_ZERO)
        return SimpleOutcome(resolved.copy(pending = null, showingResult = true))
    }

    /**
     * Bascule décimal↔Shadok.
     *
     * Seule la frappe est convertie : l'accumulateur est un [Rational], indépendant de toute
     * notation d'écriture.
     */
    fun withNotation(target: NumberNotation): SimpleSession =
        copy(entry = entry.withNotation(target))

    // ------------------------------------------------------------------ affichage

    /**
     * Ce que montre la ligne principale : la frappe si elle est en cours, sinon
     * l'accumulateur.
     *
     * Le [renderX][RpnSession.renderX] de la NPI, un cran plus simple : il n'y a pas de pile
     * sous la valeur.
     */
    fun renderValue(display: ExpressionDisplay): Rendered {
        if (entering) return renderEntry(display)
        val value = accumulator ?: return Rendered(text = "", approximate = false)
        return value.rendered(display)
    }

    /** La frappe en cours, signe compris. Vide si l'utilisateur n'a rien tapé. */
    private fun renderEntry(display: ExpressionDisplay): Rendered {
        val rendered = entry.render(display)
        if (!entryNegative || rendered.text.isEmpty()) return rendered
        return rendered.copy(text = "${ShadokFormatter.MINUS}${rendered.text}")
    }

    // --------------------------------------------------------------- persistance

    /** La frappe sous une forme que [restore] sait relire, comme `replayKeys`. */
    fun entryKeys(): String = entry.replayKeys()

    /** L'accumulateur en **fraction** — `"7"`, `"-1/3"` — ou une chaîne vide s'il n'y en a pas. */
    fun accumulatorKeys(): String = accumulator?.toString().orEmpty()

    /** L'opération en attente, réduite à son symbole. Vide s'il n'y en a pas. */
    fun pendingKeys(): String = pending?.symbol?.toString().orEmpty()

    // ------------------------------------------------------------------- interne

    /**
     * Applique l'opération en attente à la frappe, et laisse la machine prête pour la suite.
     *
     * @return `null` sur une division par zéro — l'appelant rend alors l'état d'avant.
     */
    private fun resolve(): SimpleSession? {
        val typed = entryValue()
        // Rien de tapé depuis le dernier opérateur : il n'y a pas de second opérande, donc
        // rien à calculer. L'accumulateur reste ce qu'il était.
        val value = typed ?: return clearEntry()
        val computed = if (pending == null) {
            value
        } else {
            try {
                // Un accumulateur absent avec une opération en attente ne vient que d'un état
                // restauré incohérent : zéro, comme pour un opérateur tapé avant tout nombre.
                pending.applyTo(accumulator ?: Rational.ZERO, value)
            } catch (_: ArithmeticException) {
                return null
            }
        }
        return clearEntry().copy(accumulator = computed)
    }

    /** La valeur de la frappe, signe appliqué, ou `null` si rien n'est tapé. */
    private fun entryValue(): Rational? {
        val number = entry.atoms.filterIsInstance<Atom.Number>().firstOrNull() ?: return null
        val value = number.value()
        return if (entryNegative) -value else value
    }

    private fun clearEntry(): SimpleSession = copy(entry = entry.clear(), entryNegative = false)

    /** Après un `=`, tout recommence : c'est un nouveau calcul, pas la suite du précédent. */
    private fun afterResult(): SimpleSession = if (showingResult) clear() else this

    private fun Rational.rendered(display: ExpressionDisplay): Rendered = when (display) {
        // Une valeur calculée, pas saisie : son écriture décimale peut être tronquée (un
        // tiers, par exemple). C'est la différence avec ExpressionBuffer.render, qui rend une
        // saisie décimale verbatim et donc exacte.
        ExpressionDisplay.DECIMAL -> Rendered(toDecimalString(), !hasFiniteDecimal)

        ExpressionDisplay.SHADOK_GLYPHS -> renderedInBase4(ShadokNotation.GLYPHS)

        ExpressionDisplay.SHADOK_LABELS -> renderedInBase4(ShadokNotation.LABELS)

        ExpressionDisplay.SHADOK_BASE4 -> renderedInBase4(ShadokNotation.BASE4)
    }

    private fun Rational.renderedInBase4(shadokNotation: ShadokNotation): Rendered {
        // Conversion depuis la fraction exacte, jamais depuis son écriture décimale
        // arrondie : sinon un tiers produirait des chiffres de queue faux.
        val base4 = ShadokConverter.toBase4(this)
        return Rendered(
            text = ShadokFormatter.format(base4, shadokNotation, markApproximation = false),
            approximate = base4.approximate,
        )
    }

    companion object {

        /** Une session vierge dans la notation demandée. */
        fun of(notation: NumberNotation): SimpleSession =
            SimpleSession(entry = ExpressionBuffer(notation))

        /**
         * Reconstruit une session persistée.
         *
         * La frappe repasse par les règles de saisie d'[ExpressionBuffer.replay], puis on ne
         * garde que le premier nombre : **aucune donnée stockée, même corrompue, ne peut
         * violer l'invariant « au plus un `Atom.Number` »**. Un accumulateur illisible vaut
         * `null`, un symbole d'opérateur inconnu vaut « aucune opération en attente ».
         */
        fun restore(
            entryKeys: String,
            accumulatorKeys: String,
            pendingKeys: String,
            entryNegative: Boolean,
            notation: NumberNotation,
        ): SimpleSession {
            val entry = singleNumberOf(entryKeys, notation)
            return SimpleSession(
                entry = entry,
                accumulator = Rational.parseOrNull(accumulatorKeys),
                pending = pendingKeys.firstOrNull()?.let(Operator::ofSymbolOrNull),
                // Un signe sans chiffres serait un état invisible.
                entryNegative = entryNegative && !entry.isEmpty,
            )
        }

        private fun singleNumberOf(keys: String, notation: NumberNotation): ExpressionBuffer {
            val replayed = ExpressionBuffer.replay(keys, notation)
            val number = replayed.atoms.filterIsInstance<Atom.Number>().firstOrNull()
            return ExpressionBuffer(notation, listOfNotNull(number))
        }
    }
}
