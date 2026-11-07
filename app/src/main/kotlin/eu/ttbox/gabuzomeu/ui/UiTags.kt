package eu.ttbox.gabuzomeu.ui

import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.ui.calculator.KeyAction
import eu.ttbox.gabuzomeu.ui.calculator.components.ValueWriting

/**
 * Repères de test de l'interface, réunis en un seul endroit.
 *
 * Ils sont **stables et indépendants de la langue** — contrairement aux libellés et aux
 * descriptions d'accessibilité, qui sont traduits. Deux mésaventures ont motivé leur
 * existence :
 *
 * - des assertions sur des chaînes françaises en dur passaient sur un téléphone français
 *   et échouaient sur l'émulateur `en-US` de la CI ;
 * - les touches du pavé appliquent `clearAndSetSemantics`, ce qui efface la sémantique de
 *   texte de leurs descendants : elles sont donc introuvables par `onNodeWithText`.
 */
object DisplayTags {
    const val GLYPHS = "display-glyphs"
    const val LABELS = "display-labels"
    const val DECIMAL = "display-decimal"

    /** Le message d'erreur sous l'afficheur, présent seulement quand une erreur est levée. */
    const val ERROR = "display-error"

    /** La zone de pile NPI, présente seulement dans ce mode. */
    const val STACK = "display-stack"

    /**
     * Le rang de la grande valeur — présent **seulement** quand elle est dans la pile.
     *
     * Une frappe en cours n'a pas de rang : c'est donc ce repère, et lui seul, qui distingue
     * « j'ai tapé 6 » de « 6 est empilé ». Les deux états ont longtemps été indiscernables à
     * l'écran, et c'est ce que ce repère permet enfin de tester.
     */
    const val X_LEVEL = "display-x-level"

    /**
     * Le trait qui marque la frontière de la pile.
     *
     * Repéré pour que les tests puissent vérifier **de quel côté** se trouve la grande
     * valeur : dessous elle est encore sous le doigt, dessus elle est empilée. C'est tout
     * ce qu'ENTER change à l'écran, et rien d'autre ne le dit.
     */
    const val STACK_LIMIT = "display-stack-limit"

    /** Un niveau de pile, repéré par son rang depuis le fond. */
    fun stackLevel(level: Int): String = "display-stack-$level"

    /**
     * Mode Simple : l'opération qui attend son second opérande.
     *
     * Présent **seulement** quand une opération est en attente. C'est ce repère, et lui seul,
     * qui distingue « j'ai tapé Bu » de « Bu, plus… » — deux états que la grande valeur, à
     * elle seule, dessine de la même façon.
     */
    const val PENDING = "display-pending"

    /** La zone qui ouvre le menu copier/coller à l'appui long : X, ou l'expression. */
    const val ACTIONS = "display-actions"

    /** La même zone sur un niveau de pile enfoui. */
    fun stackLevelActions(level: Int): String = "display-stack-actions-$level"
}

/** Le menu ouvert par un appui long sur une valeur affichée. */
object ActionTags {
    const val PASTE = "action-paste"
    const val SHARE = "action-share"

    /** Dérivé de l'écriture : un repère par item de copie, stable et indépendant de la langue. */
    fun copy(writing: ValueWriting): String = "action-copy-${writing.name.lowercase()}"
}

object SettingsTags {
    const val MENU_BUTTON = "settings-menu"
    const val MODE_SIMPLE = "mode-simple"
    const val MODE_CLASSIC = "mode-classic"
    const val MODE_RPN = "mode-rpn"
    const val TOGGLE_LABELS = "toggle-shadok-labels"
    const val TOGGLE_DECIMAL = "toggle-decimal"

    /** L'entrée qui mène à « Comprendre les Shadoks ». */
    const val HELP = "settings-help"

    /** L'entrée qui mène au jeu. */
    const val GAME = "settings-game"
}

/** Le jeu d'apprentissage. */
object GameTags {
    const val SCREEN = "game-screen"
    const val CLOSE = "game-close"
    const val QUESTION = "game-question"
    const val SCORE = "game-score"

    /** Un bouton de réponse, repéré par son rang à l'écran. */
    fun choice(index: Int): String = "game-choice-$index"
}

/** L'écran d'apprentissage. */
object HelpTags {
    const val SCREEN = "help-screen"
    const val CLOSE = "help-close"

    /** Le lien sortant vers la vidéo de l'INA, en tête d'écran. */
    const val VIDEO = "help-video"

    /** Le dernier bloc de la leçon : ce qui prouve que l'écran défile jusqu'au bout. */
    const val ENDING = "help-ending"
}

/**
 * Le sélecteur décimal / Shadok.
 *
 * Il n'avait pas de repère : les tests le cherchaient par son libellé. Ça a tenu jusqu'au jour
 * où le menu a gagné un item nommé « Décimal », et où deux nœuds portaient le même texte —
 * échec net, et pour une raison qui n'avait rien à voir avec ce qui était testé. Un repère ne
 * peut pas devenir ambigu comme un mot.
 */
object NotationTags {
    fun of(notation: NumberNotation): String = "notation-${notation.name.lowercase()}"
}

object KeypadTags {
    /** Dérivé de l'action : unique, stable, et lisible dans un rapport d'échec. */
    fun of(action: KeyAction): String = when (action) {
        is KeyAction.Digit -> "key-digit-${action.character.code}"
        is KeyAction.Op -> "key-op-${action.operator.name}"
        KeyAction.Separator -> "key-separator"
        KeyAction.LeftParen -> "key-left-paren"
        KeyAction.RightParen -> "key-right-paren"
        KeyAction.Delete -> "key-delete"
        KeyAction.Clear -> "key-clear"
        KeyAction.Evaluate -> "key-equals"
        KeyAction.Enter -> "key-enter"
        KeyAction.Swap -> "key-swap"
        KeyAction.Drop -> "key-drop"
        KeyAction.Negate -> "key-negate"
    }
}
