package eu.ttbox.gabuzomeu.core.eval

/** Levée en interne par [Parser] ; convertie en [EvalResult.Failure] par l'évaluateur. */
internal class ParseException(val error: EvalError) : Exception(error.name)

/**
 * Analyseur à descente récursive.
 *
 * ```
 * expression := terme      (('+' | '−') terme)*
 * terme      := facteur    (('×' | '÷') facteur)*
 * facteur    := '−' facteur | primaire
 * primaire   := nombre | '(' expression ')'
 * ```
 *
 * Le moins unaire est traité au niveau `facteur`, ce qui rend `5+−3` analysable — la
 * saisie que la règle 3 du tampon autorise explicitement.
 *
 * La récursion est bornée à [MAX_DEPTH] niveaux de parenthèses ou de moins unaires : au-delà,
 * l'expression est refusée en [EvalError.SYNTAX]. Sans cette borne, vingt mille `(` collés
 * ou restaurés épuisaient la pile d'appels — un `StackOverflowError` que rien ne rattrapait.
 */
internal class Parser(private val atoms: List<Atom>) {

    private var position = 0
    private var depth = 0

    fun parse(): Expr {
        val expression = parseExpression()
        if (position != atoms.size) fail(EvalError.SYNTAX)
        return expression
    }

    private fun parseExpression(): Expr {
        var left = parseTerm()
        while (true) {
            val operator = peekOperator(Operator.PLUS, Operator.MINUS) ?: return left
            position++
            left = Expr.Binary(left, operator, parseTerm())
        }
    }

    private fun parseTerm(): Expr {
        var left = parseFactor()
        while (true) {
            val operator = peekOperator(Operator.TIMES, Operator.DIVIDE) ?: return left
            position++
            left = Expr.Binary(left, operator, parseFactor())
        }
    }

    private fun parseFactor(): Expr {
        val atom = atoms.getOrNull(position) ?: fail(EvalError.SYNTAX)
        // Seul point de récursion : `facteur` se rappelle lui-même (moins unaire) ou, via
        // `primaire`, rappelle `expression`. Le compter ici borne toute la descente.
        if (++depth > MAX_DEPTH) fail(EvalError.SYNTAX)
        try {
            if (atom is Atom.Op && atom.operator == Operator.MINUS) {
                position++
                return Expr.Negate(parseFactor())
            }
            return parsePrimary()
        } finally {
            depth--
        }
    }

    private fun parsePrimary(): Expr {
        val atom = atoms.getOrNull(position) ?: fail(EvalError.SYNTAX)
        return when (atom) {
            is Atom.Number -> {
                position++
                Expr.Literal(atom.value())
            }

            Atom.LeftParen -> {
                position++
                val inner = parseExpression()
                if (atoms.getOrNull(position) != Atom.RightParen) {
                    fail(EvalError.UNBALANCED_PARENTHESES)
                }
                position++
                inner
            }

            is Atom.Op, Atom.RightParen -> fail(EvalError.SYNTAX)
        }
    }

    /** Renvoie [Nothing] : utilisable comme expression, y compris après un `?:`. */
    private fun fail(error: EvalError): Nothing = throw ParseException(error)

    private fun peekOperator(vararg accepted: Operator): Operator? {
        val atom = atoms.getOrNull(position)
        if (atom !is Atom.Op) return null
        return atom.operator.takeIf { it in accepted }
    }

    companion object {
        /**
         * Profondeur d'imbrication maximale : bien plus que ce qu'un écran affiche, bien
         * moins que ce que la pile d'appels supporte.
         */
        const val MAX_DEPTH: Int = 256
    }
}
