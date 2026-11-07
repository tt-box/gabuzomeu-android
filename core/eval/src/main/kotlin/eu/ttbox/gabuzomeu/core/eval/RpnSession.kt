package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational
import eu.ttbox.gabuzomeu.core.shadok.ShadokConverter
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation

/**
 * Le résultat d'une frappe en NPI : le nouvel état, et l'erreur éventuelle.
 *
 * Quand [error] n'est pas nul, [session] est **l'état d'avant, intact**. Une pile trop
 * courte ou une division par zéro ne doit jamais faire perdre à l'utilisateur les valeurs
 * qu'il a empilées : il corrige et recommence.
 */
data class RpnOutcome(val session: RpnSession, val error: EvalError? = null)

/**
 * La calculatrice en notation polonaise inverse : **la source de vérité unique** du mode.
 *
 * L'équivalent d'[ExpressionBuffer] pour la NPI, et construit sur lui : le nombre en cours
 * de frappe *est* un [ExpressionBuffer] restreint à un seul [Atom.Number]. Toutes les
 * règles de saisie d'un opérande — un seul séparateur, le zéro de tête matérialisé, le
 * filtrage des chiffres selon la notation, la conversion décimal↔Shadok en pleine frappe —
 * sont donc déjà écrites et déjà testées ; il n'y avait rien à réimplémenter.
 *
 * Ce qui reste propre à la NPI est la pile et l'absence de parseur : en postfixe, l'ordre
 * de frappe *est* l'ordre de calcul, il n'y a ni priorité ni parenthèses à résoudre.
 *
 * @property entry le nombre en cours de frappe. Contient au plus un [Atom.Number] ;
 *   l'invariant tient parce que seuls [appendDigit], [appendSeparator], [appendNumber] et
 *   [deleteLast] y touchent — jamais `appendOperator` ni les parenthèses.
 * @property entryNegative le signe de la frappe, porté ici et non dans les chiffres.
 *   `Atom.Number.digits` n'a pas de signe : en infixe un négatif est un `Atom.Op(MINUS)`,
 *   ce qui n'a aucun sens sur un opérande isolé. D'où la touche ± plutôt qu'un `−`.
 */
data class RpnSession(
    val stack: RpnStack = RpnStack(),
    val entry: ExpressionBuffer = ExpressionBuffer(),
    val entryNegative: Boolean = false,
) {

    /**
     * La notation de saisie, **lue sur le tampon** et non stockée en double.
     *
     * Un champ séparé pourrait contredire `entry.notation` — et l'a fait : la session se
     * déclarait Shadok tandis que son tampon restait décimal, si bien que le pavé Shadok
     * refusait ses propres glyphes et acceptait des chiffres décimaux. Dérivée, la
     * contradiction n'est plus exprimable.
     */
    val notation: NumberNotation get() = entry.notation

    /** Une frappe est en cours. **Dérivé** : un état de moins à tenir synchronisé. */
    val entering: Boolean get() = !entry.isEmpty

    // -------------------------------------------------------------------- saisie

    /**
     * Ajoute un chiffre à la frappe.
     *
     * La pile n'est pas touchée : c'est ce qui fait qu'après `3 ENTER 4 +`, taper `5` puis
     * `×` donne 35 — le résultat 7 est resté empilé et devient l'opérande gauche.
     */
    fun appendDigit(digit: Char): RpnSession = copy(entry = entry.appendDigit(digit))

    fun appendSeparator(): RpnSession = copy(entry = entry.appendSeparator())

    /**
     * Un nombre collé, avec sa valeur exacte éventuelle : voir [ExpressionBuffer.appendNumber].
     * L'invariant « au plus un nombre » tient : le collage prolonge ou remplace la frappe.
     */
    fun appendNumber(digits: String, exact: Rational? = null): RpnSession =
        copy(entry = entry.appendNumber(digits, exact))

    /**
     * Efface le dernier caractère **de la frappe uniquement**.
     *
     * Jamais la pile : c'est le rôle de [dropTop]. Deux touches, deux effets, aucune
     * ambiguïté — un ⌫ qui dépilerait silencieusement une fois la frappe épuisée serait
     * un moyen commode de détruire un calcul sans le vouloir.
     */
    fun deleteLast(): RpnSession {
        val shortened = entry.deleteLast()
        // Un signe qui survivrait à une frappe vidée serait invisible, et resurgirait au
        // chiffre suivant.
        return copy(entry = shortened, entryNegative = entryNegative && !shortened.isEmpty)
    }

    /**
     * ENTER : empile la frappe, ou **duplique le sommet** si aucune frappe n'est en cours.
     *
     * La duplication est la convention HP, et elle est utile : `ENTER ×` élève au carré.
     * Sur un état entièrement vide, on empile zéro — également le comportement HP.
     */
    fun enter(): RpnSession {
        val committed = commitEntry()
        if (entering) return committed
        return committed.copy(stack = committed.stack.push(committed.stack.top ?: Rational.ZERO))
    }

    /**
     * Applique un opérateur aux deux valeurs du sommet, après avoir empilé la frappe.
     *
     * Le sommet est l'opérande **droit** : `10 ENTER 3 −` vaut 7.
     */
    fun apply(operator: Operator): RpnOutcome {
        val committed = commitEntry()
        val computed = try {
            committed.stack.apply(operator)
        } catch (_: ArithmeticException) {
            return RpnOutcome(this, EvalError.DIVISION_BY_ZERO)
        }
        val reduced = computed ?: return RpnOutcome(this, EvalError.STACK_UNDERFLOW)
        return RpnOutcome(committed.copy(stack = reduced))
    }

    /** x↔y : échange les deux valeurs du sommet, la frappe étant d'abord empilée. */
    fun swap(): RpnOutcome {
        val committed = commitEntry()
        val swapped =
            committed.stack.swapTop() ?: return RpnOutcome(this, EvalError.STACK_UNDERFLOW)
        return RpnOutcome(committed.copy(stack = swapped))
    }

    /** DROP : abandonne la frappe en cours, ou à défaut dépile le sommet. */
    fun dropTop(): RpnSession = if (entering) clearEntry() else copy(stack = stack.dropTop())

    /** ± : change le signe de la frappe, ou à défaut celui du sommet. */
    fun negate(): RpnSession =
        if (entering) copy(entryNegative = !entryNegative) else copy(stack = stack.negateTop())

    /** C : vide la frappe **et** la pile. */
    fun clear(): RpnSession = clearEntry().copy(stack = stack.clear())

    /**
     * Bascule décimal↔Shadok.
     *
     * Seule la frappe est convertie : la pile contient des [Rational], indépendants de
     * toute notation d'écriture.
     */
    fun withNotation(target: NumberNotation): RpnSession = copy(entry = entry.withNotation(target))

    // ------------------------------------------------------------------ affichage

    /**
     * Le registre **X** : ce que montre la ligne principale de l'afficheur.
     *
     * C'est la frappe si elle est en cours, sinon le sommet de la pile. Autrement dit la
     * convention des HP, où l'afficheur *est* X et où taper un chiffre le remplace : après
     * `6 ENTER 7 ×`, la grande ligne montre 42 sans qu'on ait eu à appuyer sur un « = »
     * qui n'existe pas. Sans cette règle, elle resterait vide après chaque opération.
     */
    fun renderX(display: ExpressionDisplay): Rendered {
        if (entering) return renderEntry(display)
        val summit = stack.top ?: return Rendered(text = "", approximate = false)
        return summit.rendered(display)
    }

    /** Les niveaux situés **sous** X, du fond de pile vers X. */
    fun renderBelowX(display: ExpressionDisplay): List<Rendered> {
        val below = if (entering) stack.values else stack.values.dropLast(1)
        return below.map { value -> value.rendered(display) }
    }

    /** La frappe en cours, signe compris. Vide si l'utilisateur n'a rien tapé. */
    fun renderEntry(display: ExpressionDisplay): Rendered {
        val rendered = entry.render(display)
        if (!entryNegative || rendered.text.isEmpty()) return rendered
        return rendered.copy(text = "${ShadokFormatter.MINUS}${rendered.text}")
    }

    /** La pile, du fond vers le sommet — l'ordre d'affichage de bas en haut, inversé. */
    fun renderStack(display: ExpressionDisplay): List<Rendered> =
        stack.values.map { value -> value.rendered(display) }

    // --------------------------------------------------------------- persistance

    /** La frappe sous une forme que [restore] sait relire, comme `replayKeys`. */
    fun entryKeys(): String = entry.replayKeys()

    /** La valeur exacte de la frappe, comme [ExpressionBuffer.exactKeys]. */
    fun entryExactKeys(): String = entry.exactKeys()

    /**
     * Rend à la frappe restaurée la valeur exacte écrite par [entryExactKeys]. À chaîner
     * après [restore] ; une valeur invalide est ignorée (voir [ExpressionBuffer.withExactKeys]).
     */
    fun withEntryExactKeys(exactKeys: String): RpnSession =
        copy(entry = entry.withExactKeys(exactKeys))

    // ----------------------------------------------------------------- interne

    /** La valeur de la frappe, signe appliqué, ou `null` si rien n'est tapé. */
    private fun entryValue(): Rational? {
        val number = entry.atoms.filterIsInstance<Atom.Number>().firstOrNull() ?: return null
        val value = number.value()
        return if (entryNegative) -value else value
    }

    /** Empile la frappe si elle existe. Sans frappe, l'état est rendu tel quel. */
    private fun commitEntry(): RpnSession {
        val value = entryValue() ?: return this
        return clearEntry().copy(stack = stack.push(value))
    }

    private fun clearEntry(): RpnSession = copy(entry = entry.clear(), entryNegative = false)

    private fun Rational.rendered(display: ExpressionDisplay): Rendered = when (display) {
        // Une valeur de pile est calculée, pas saisie : son écriture décimale peut être
        // tronquée (un tiers, par exemple). C'est la différence avec
        // ExpressionBuffer.render, qui rend une saisie décimale verbatim et donc exacte.
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
        fun of(notation: NumberNotation): RpnSession =
            RpnSession(entry = ExpressionBuffer(notation))

        /**
         * Reconstruit une session persistée.
         *
         * La frappe repasse par les règles de saisie d'[ExpressionBuffer.replay], puis on
         * ne garde que le premier nombre : **aucune donnée stockée, même corrompue, ne
         * peut violer l'invariant « au plus un `Atom.Number` »**.
         */
        fun restore(
            stackKeys: String,
            entryKeys: String,
            entryNegative: Boolean,
            notation: NumberNotation,
        ): RpnSession {
            val entry = singleNumberOf(entryKeys, notation)
            return RpnSession(
                stack = RpnStack.restore(stackKeys),
                entry = entry,
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
