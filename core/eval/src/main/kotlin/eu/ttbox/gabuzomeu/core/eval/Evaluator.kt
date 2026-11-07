package eu.ttbox.gabuzomeu.core.eval

import eu.ttbox.gabuzomeu.core.shadok.Rational

/**
 * Évalue une expression en arithmétique **exacte**.
 *
 * Remplace la bibliothèque `arity` du projet d'origine, qui calculait en `double` et
 * n'était de toute façon plus récupérable (jar jamais publié sur un dépôt public,
 * installé à la main par `mvnrepo.sh`).
 *
 * Travailler sur des [Rational] plutôt que des flottants n'est pas un luxe ici : c'est
 * ce qui permet à `1÷3` de rester exactement un tiers et de se rendre `Ga.BuBuBu…` en
 * base 4, au lieu de propager les chiffres de queue d'un arrondi décimal.
 */
object Evaluator {

    fun evaluate(buffer: ExpressionBuffer): EvalResult = evaluate(buffer.atoms)

    fun evaluate(atoms: List<Atom>): EvalResult {
        // Un opérateur en attente de son opérande est ignoré : appuyer sur « = » après
        // « 5+ » donne 5. Même comportement que Logic.java:242 dans le code d'origine.
        // Une parenthèse ouvrante restée vide l'est aussi, avec ce qui la précède :
        // « 2×( » et « 2×(− » donnent 2, pas une erreur de syntaxe.
        val trimmed = atoms.dropLastWhile { it is Atom.Op || it is Atom.LeftParen }
        if (trimmed.isEmpty()) return EvalResult.Failure(EvalError.EMPTY)

        // Parenthèses laissées ouvertes : on les ferme, comme le faisait la
        // calculatrice AOSP dont ce projet est issu.
        val balanced = trimmed + List(unclosedParenCount(trimmed)) { Atom.RightParen }

        return try {
            EvalResult.Success(compute(Parser(balanced).parse()))
        } catch (failure: ParseException) {
            EvalResult.Failure(failure.error)
        } catch (_: ArithmeticException) {
            EvalResult.Failure(EvalError.DIVISION_BY_ZERO)
        }
    }

    private fun unclosedParenCount(atoms: List<Atom>): Int {
        val opened = atoms.count { it is Atom.LeftParen }
        val closed = atoms.count { it is Atom.RightParen }
        return (opened - closed).coerceAtLeast(0)
    }

    private fun compute(expression: Expr): Rational = when (expression) {
        is Expr.Literal -> expression.value

        is Expr.Negate -> -compute(expression.operand)

        // Une division par zéro y lève ArithmeticException, rattrapée plus haut.
        is Expr.Binary -> expression.operator.applyTo(
            left = compute(expression.left),
            right = compute(expression.right),
        )
    }
}
