package eu.ttbox.gabuzomeu.ui.calculator

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.ttbox.gabuzomeu.core.eval.Atom
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.ClipboardNumber
import eu.ttbox.gabuzomeu.core.eval.EvalError
import eu.ttbox.gabuzomeu.core.eval.EvalResult
import eu.ttbox.gabuzomeu.core.eval.Evaluator
import eu.ttbox.gabuzomeu.core.eval.ExpressionBuffer
import eu.ttbox.gabuzomeu.core.eval.ExpressionDisplay
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.core.eval.PastedNumber
import eu.ttbox.gabuzomeu.core.eval.Rendered
import eu.ttbox.gabuzomeu.core.eval.RpnOutcome
import eu.ttbox.gabuzomeu.core.eval.RpnSession
import eu.ttbox.gabuzomeu.core.eval.SimpleSession
import eu.ttbox.gabuzomeu.core.shadok.ShadokFormatter
import eu.ttbox.gabuzomeu.data.CalculatorPreferences
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.data.SessionStore
import eu.ttbox.gabuzomeu.data.StoredRpn
import eu.ttbox.gabuzomeu.data.StoredSession
import eu.ttbox.gabuzomeu.data.StoredSimple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ce qu'une touche du pavé demande de faire.
 *
 * Certaines actions n'ont de sens que dans un mode : les parenthèses et le `=` sont propres
 * à l'infixe, [Enter], [Swap], [Drop] et [Negate] à la NPI. Le pavé n'affiche jamais une
 * touche hors de son mode, mais le `when` du ViewModel reste exhaustif — c'est le
 * compilateur qui garantit qu'aucune action ne tombe dans l'oubli.
 */
sealed interface KeyAction {
    data class Digit(val character: Char) : KeyAction
    data class Op(val operator: Operator) : KeyAction
    data object Separator : KeyAction
    data object LeftParen : KeyAction
    data object RightParen : KeyAction
    data object Delete : KeyAction
    data object Clear : KeyAction
    data object Evaluate : KeyAction

    /** NPI : empile la frappe, ou duplique le sommet. */
    data object Enter : KeyAction

    /** NPI : échange les deux valeurs du sommet. */
    data object Swap : KeyAction

    /** NPI : abandonne la frappe, ou dépile le sommet. */
    data object Drop : KeyAction

    /** NPI : change le signe de la frappe, ou du sommet. */
    data object Negate : KeyAction
}

/**
 * Portée de l'écriture finale, à l'échelle du processus.
 *
 * `viewModelScope` est déjà annulé quand `onCleared` s'exécute : la dernière écriture doit
 * donc partir d'ailleurs, sans quoi les frappes des 300 dernières millisecondes avant un
 * retour arrière étaient perdues.
 */
private val processScope = CoroutineScope(SupervisorJob())

class CalculatorViewModel(
    private val sessionStore: SessionStore,
    private val savedStateHandle: SavedStateHandle,
    private val flushScope: CoroutineScope = processScope,
) : ViewModel() {

    private var mode = CalculationMode.CLASSIC

    /** Mode infixe. Conservé même quand un autre mode est affiché : basculer ne détruit rien. */
    private var buffer = ExpressionBuffer()

    /** Mode NPI. Conservé de même, avec sa pile et sa frappe en cours. */
    private var rpn = RpnSession()

    /** Mode Simple. Conservé de même, avec son accumulateur et son opération en attente. */
    private var simple = SimpleSession()

    private var showingResult = false
    private var error: EvalError? = null
    private var decimalApproximate = false
    private var settings = DisplaySettings()

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    /** Écritures disque regroupées : inutile de solliciter DataStore à chaque frappe. */
    private val persistRequests = MutableStateFlow<StoredSession?>(null)

    /** La dernière session effectivement écrite : `onCleared` n'écrit que si elle a changé. */
    private var lastPersisted: StoredSession? = null

    /** La session disque a-t-elle été lue (ou rendue inutile par SavedStateHandle) ? */
    private var sessionLoaded = false

    /**
     * Mode et notation choisis par l'utilisateur **avant** la fin de la lecture disque.
     *
     * Le menu et le sélecteur sont actifs dès le premier rendu : un choix fait pendant la
     * lecture asynchrone était jusqu'ici écrasé par la session restaurée.
     */
    private var modeChosenEarly: CalculationMode? = null
    private var notationChosenEarly: NumberNotation? = null

    init {
        // Les réglages d'affichage vivent hors de l'expression : on les suit en continu.
        viewModelScope.launch {
            sessionStore.settings.collect { stored ->
                settings = stored
                publish()
            }
        }

        val saved = savedInstanceSession()
        if (saved != null) {
            // Retour après mort du processus. SavedStateHandle est synchrone, donc
            // l'état est là immédiatement : pas de clignotement d'écran vide.
            restore(saved)
        } else {
            publish()
        }

        viewModelScope.launch {
            if (saved == null) {
                val stored = sessionStore.session.first()
                // Ne pas écraser une saisie commencée pendant la lecture asynchrone. Le mode
                // et la notation choisis pendant cette lecture l'emportent, eux, sur le
                // souvenir : ce sont des choix explicites de l'utilisateur.
                if (untouched) {
                    // Rien à réécrire si la session restaurée est celle du disque.
                    lastPersisted = stored
                    restore(stored.copy(mode = modeChosenEarly ?: stored.mode))
                    notationChosenEarly?.let(::onNotationChange)
                }
            }

            // L'écriture disque n'est branchée qu'une fois la session d'origine connue : une
            // session vide publiée au démarrage écraserait sinon celle qu'on est en train de
            // lire. `persistRequests` étant un `StateFlow`, la collecte repart de la dernière
            // valeur publiée — rien de ce qui a été tapé entre-temps ne se perd.
            sessionLoaded = true

            // Anti-rebond : collectLatest annule le délai dès qu'une frappe suivante
            // arrive, donc seule la dernière valeur d'une rafale atteint le disque.
            // (Flow.debounce ferait la même chose mais reste une API @FlowPreview.)
            persistRequests.filterNotNull().collectLatest { request ->
                if (request == lastPersisted) return@collectLatest
                delay(PERSIST_DEBOUNCE_MS)
                sessionStore.save(request)
                lastPersisted = request
            }
        }
    }

    /**
     * Dernière écriture, quand l'écran est quitté avant la fin de l'anti-rebond.
     *
     * `NonCancellable` et [flushScope] plutôt que `viewModelScope`, déjà annulé ici : c'est
     * ce qui garantit que la frappe tapée juste avant le retour arrière atteint le disque.
     */
    override fun onCleared() {
        val pending = persistRequests.value
        if (sessionLoaded && pending != null && pending != lastPersisted) {
            flushScope.launch(NonCancellable) { sessionStore.save(pending) }
        }
    }

    fun onKey(action: KeyAction) {
        // Toute nouvelle frappe acquitte l'erreur affichée.
        error = null

        when (mode) {
            // Le routage du mode Simple est une fonction pure, dans SimpleKeys.kt : la
            // session étant immuable, il n'y a qu'à retenir ce qu'elle rend.
            CalculationMode.SIMPLE -> {
                val outcome = simple.handle(action)
                simple = outcome.session
                error = outcome.error
            }

            CalculationMode.CLASSIC -> onClassicKey(action)

            CalculationMode.RPN -> onRpnKey(action)
        }

        publish()
    }

    /**
     * Insère un nombre venu du presse-papiers.
     *
     * Le collage ne pose **aucun état nouveau** : il rejoue `appendDigit` / `appendSeparator`
     * caractère par caractère, exactement comme la restauration d'une session persistée. Un
     * tampon invalide ne peut donc pas naître d'un texte arbitraire.
     *
     * Un texte illisible est ignoré sans bruit. L'interface ne grise « Coller » que si le
     * presse-papiers ne contient pas de texte du tout : lire son contenu pour l'analyser
     * déclencherait, dès l'ouverture du menu, l'avertissement d'Android 12+ « collé depuis
     * le presse-papiers ».
     */
    fun onPaste(text: String) {
        val pasted = ClipboardNumber.parseOrNull(text, buffer.notation) ?: return
        error = null

        when (mode) {
            CalculationMode.SIMPLE -> pasteIntoSimple(pasted)
            CalculationMode.CLASSIC -> pasteIntoBuffer(pasted)
            CalculationMode.RPN -> pasteIntoRpn(pasted)
        }

        publish()
    }

    /** Le collage démarre une frappe, comme si les chiffres avaient été tapés. */
    private fun pasteIntoSimple(pasted: PastedNumber) {
        pasted.keys.forEach { key ->
            simple = if (key == ShadokFormatter.SEPARATOR) {
                simple.appendSeparator()
            } else {
                simple.appendDigit(key)
            }
        }
        // Après les chiffres, comme en NPI : `negate` porte le signe de la frappe en cours.
        // Le pavé n'a pas de ±, mais un nombre collé négatif ne doit pas perdre son signe.
        if (pasted.negative) simple = simple.negate()
    }

    private fun pasteIntoBuffer(pasted: PastedNumber) {
        // Règle 5, comme pour un chiffre tapé : un nombre après un résultat repart de zéro.
        if (showingResult) reset()
        // Le signe passe par l'opérateur, seule forme qu'un négatif prend en infixe. Le
        // tampon l'acceptera ou le refusera selon ses propres règles — « 6−» collé après
        // « 2× » donne « 2×−6 », et un moins en tête d'expression est licite.
        if (pasted.negative) buffer = buffer.appendOperator(Operator.MINUS)
        buffer = replayInto(buffer, pasted.keys)
    }

    private fun pasteIntoRpn(pasted: PastedNumber) {
        pasted.keys.forEach { key ->
            rpn = if (key == ShadokFormatter.SEPARATOR) {
                rpn.appendSeparator()
            } else {
                rpn.appendDigit(key)
            }
        }
        // Après les chiffres : `negate` bascule le signe de la frappe en cours, alors qu'avant
        // eux il aurait changé celui du sommet de pile.
        if (pasted.negative) rpn = rpn.negate()
    }

    private fun replayInto(target: ExpressionBuffer, keys: String): ExpressionBuffer =
        keys.fold(target) { buffer, key ->
            if (key == ShadokFormatter.SEPARATOR) {
                buffer.appendSeparator()
            } else {
                buffer.appendDigit(key)
            }
        }

    fun onNotationChange(notation: NumberNotation) {
        if (!sessionLoaded) notationChosenEarly = notation
        // Les trois modes suivent : le sélecteur décimal/Shadok est un axe unique, il ne
        // doit pas laisser derrière lui un mode inactif écrit dans l'autre notation — un
        // pavé Shadok face à une frappe décimale refuserait ses propres glyphes.
        buffer = buffer.withNotation(notation)
        rpn = rpn.withNotation(notation)
        simple = simple.withNotation(notation)
        publish()
    }

    /**
     * Bascule d'un mode de calcul à l'autre.
     *
     * L'état des deux autres reste en place : revenir retrouve exactement l'expression, la
     * pile ou le calcul en cours qu'on y avait laissé.
     */
    fun onModeChange(target: CalculationMode) {
        if (target == mode) return
        if (!sessionLoaded) modeChosenEarly = target
        mode = target
        error = null
        publish()
    }

    fun onSettingsChange(updated: DisplaySettings) {
        settings = updated
        publish()
        // Écriture immédiate : un réglage se change rarement, l'anti-rebond de la saisie
        // n'a pas de raison de s'appliquer ici.
        viewModelScope.launch { sessionStore.saveSettings(updated) }
    }

    // ------------------------------------------------------------- mode classique

    private fun onClassicKey(action: KeyAction) {
        when (action) {
            is KeyAction.Digit -> {
                // Règle 5 — un chiffre après un résultat repart de zéro, un opérateur
                // prolonge le résultat (`Logic.acceptInsert`, `Logic.java:158-160`).
                if (showingResult) reset()
                buffer = buffer.appendDigit(action.character)
            }

            KeyAction.Separator -> {
                if (showingResult) reset()
                buffer = buffer.appendSeparator()
            }

            is KeyAction.Op -> {
                continueFromResult()
                buffer = buffer.appendOperator(action.operator)
            }

            KeyAction.LeftParen -> {
                if (showingResult) reset()
                buffer = buffer.appendLeftParen()
            }

            KeyAction.RightParen -> {
                continueFromResult()
                buffer = buffer.appendRightParen()
            }

            KeyAction.Delete -> {
                continueFromResult()
                buffer = buffer.deleteLast()
            }

            KeyAction.Clear -> reset()

            KeyAction.Evaluate -> evaluate()

            // Touches propres à la NPI : le pavé classique ne les affiche pas.
            KeyAction.Enter, KeyAction.Swap, KeyAction.Drop, KeyAction.Negate -> Unit
        }
    }

    private fun reset() {
        buffer = buffer.clear()
        showingResult = false
        decimalApproximate = false
    }

    /** Le résultat affiché redevient un opérande ordinaire que l'on prolonge. */
    private fun continueFromResult() {
        showingResult = false
        decimalApproximate = false
    }

    private fun evaluate() {
        when (val result = Evaluator.evaluate(buffer)) {
            is EvalResult.Failure -> error = result.error

            is EvalResult.Success -> {
                val value = result.value
                // Le résultat devient le nouvel opérande, écrit en décimal : c'est la
                // seule notation qui rend exactement toute valeur issue d'une saisie
                // Shadok, puisque 4 = 2² et que 2 divise 10. Pour le reste — un tiers —
                // la fraction exacte voyage avec son écriture arrondie : `1÷3 = ×3 =`
                // donne 1, et non 0.99999999999999999999.
                val exact = value.takeUnless { it.hasFiniteDecimal }
                buffer = ExpressionBuffer(
                    notation = buffer.notation,
                    atoms = listOf(
                        Atom.Number(NumberNotation.DECIMAL, value.toDecimalString(), exact),
                    ),
                )
                showingResult = true
                // Un tiers, par exemple, n'a pas d'écriture décimale finie : le signaler
                // plutôt que de présenter un arrondi comme une valeur exacte.
                decimalApproximate = !value.hasFiniteDecimal
            }
        }
    }

    // ------------------------------------------------------------------ mode NPI

    private fun onRpnKey(action: KeyAction) {
        when (action) {
            is KeyAction.Digit -> rpn = rpn.appendDigit(action.character)

            KeyAction.Separator -> rpn = rpn.appendSeparator()

            // Un opérateur peut échouer — pile trop courte, division par zéro — et
            // l'échec laisse alors la session intacte : rien n'est perdu.
            is KeyAction.Op -> record(rpn.apply(action.operator))

            KeyAction.Swap -> record(rpn.swap())

            KeyAction.Enter -> rpn = rpn.enter()

            KeyAction.Drop -> rpn = rpn.dropTop()

            KeyAction.Negate -> rpn = rpn.negate()

            KeyAction.Delete -> rpn = rpn.deleteLast()

            KeyAction.Clear -> rpn = rpn.clear()

            // Touches propres à l'infixe : le pavé NPI ne les affiche pas. En postfixe,
            // il n'y a ni parenthèses ni « = » — l'ordre de frappe est l'ordre de calcul.
            KeyAction.Evaluate, KeyAction.LeftParen, KeyAction.RightParen -> Unit
        }
    }

    /**
     * Retient le résultat d'une opération de pile.
     *
     * En cas d'échec, `outcome.session` est l'état d'avant : l'affectation est donc sans
     * effet et seule l'erreur change. Rien à défaire.
     */
    private fun record(outcome: RpnOutcome) {
        rpn = outcome.session
        error = outcome.error
    }

    // ------------------------------------------------------------------ publication

    private fun publish() {
        val lines = renderLines()

        _uiState.value = CalculatorUiState(
            mode = mode,
            notation = buffer.notation,
            decimal = lines.decimal.text,
            glyphs = lines.glyphs.text,
            labels = lines.labels.text,
            base4 = lines.base4.text,
            shadokApproximate = lines.glyphs.approximate,
            decimalApproximate = when (mode) {
                // En infixe, une saisie décimale est rendue verbatim, donc toujours
                // exacte : seule l'évaluation peut produire un arrondi, et elle renseigne
                // le champ. Dans les deux autres modes, la grande valeur peut être une
                // valeur calculée — le sommet de pile, ou l'accumulateur.
                CalculationMode.CLASSIC -> decimalApproximate

                CalculationMode.SIMPLE, CalculationMode.RPN -> lines.decimal.approximate
            },
            stack = stackLevels(),
            // En infixe la notion n'existe pas : l'expression entière est à l'écran, donc
            // rien n'y est « en attente de validation ».
            entering = when (mode) {
                CalculationMode.SIMPLE -> simple.entering
                CalculationMode.RPN -> rpn.entering
                CalculationMode.CLASSIC -> false
            },
            pending = if (mode == CalculationMode.SIMPLE) simple.pending else null,
            error = error,
            showingResult = when (mode) {
                CalculationMode.SIMPLE -> simple.showingResult

                CalculationMode.CLASSIC -> showingResult

                // En NPI il n'y a pas de « = » : la pile ne montre jamais un résultat figé.
                CalculationMode.RPN -> false
            },
            settings = settings,
        )

        val session = storedSession()
        saveInstanceState(session)
        persistRequests.value = session
    }

    /** Les quatre projections de la ligne principale, selon le mode affiché. */
    private fun renderLines(): Lines = when (mode) {
        CalculationMode.SIMPLE -> Lines(
            decimal = simple.renderValue(ExpressionDisplay.DECIMAL),
            glyphs = simple.renderValue(ExpressionDisplay.SHADOK_GLYPHS),
            labels = simple.renderValue(ExpressionDisplay.SHADOK_LABELS),
            base4 = simple.renderValue(ExpressionDisplay.SHADOK_BASE4),
        )

        CalculationMode.CLASSIC -> Lines(
            decimal = buffer.render(ExpressionDisplay.DECIMAL),
            glyphs = buffer.render(ExpressionDisplay.SHADOK_GLYPHS),
            labels = buffer.render(ExpressionDisplay.SHADOK_LABELS),
            base4 = buffer.render(ExpressionDisplay.SHADOK_BASE4),
        )

        CalculationMode.RPN -> Lines(
            decimal = rpn.renderX(ExpressionDisplay.DECIMAL),
            glyphs = rpn.renderX(ExpressionDisplay.SHADOK_GLYPHS),
            labels = rpn.renderX(ExpressionDisplay.SHADOK_LABELS),
            base4 = rpn.renderX(ExpressionDisplay.SHADOK_BASE4),
        )
    }

    /** Les niveaux sous X. Vide hors NPI : la calculatrice classique n'a pas de pile. */
    private fun stackLevels(): List<StackLevel> {
        if (mode != CalculationMode.RPN) return emptyList()
        val decimal = rpn.renderBelowX(ExpressionDisplay.DECIMAL)
        val glyphs = rpn.renderBelowX(ExpressionDisplay.SHADOK_GLYPHS)
        val labels = rpn.renderBelowX(ExpressionDisplay.SHADOK_LABELS)
        // Comme la ligne de X : jamais affichée, mais un appui long sur un niveau enfoui
        // doit pouvoir le copier dans les quatre écritures.
        val base4 = rpn.renderBelowX(ExpressionDisplay.SHADOK_BASE4)
        return decimal.indices.map { level ->
            StackLevel(
                glyphs = glyphs[level].text,
                labels = labels[level].text,
                decimal = decimal[level].text,
                base4 = base4[level].text,
                shadokApproximate = glyphs[level].approximate,
                decimalApproximate = decimal[level].approximate,
            )
        }
    }

    // ------------------------------------------------------------------ persistance

    /** Rien n'a encore été saisi, dans aucun des trois modes. */
    private val untouched: Boolean
        get() = buffer.isEmpty && rpn.stack.isEmpty && !rpn.entering && simple.isPristine

    private fun storedSession(): StoredSession = StoredSession(
        keys = buffer.replayKeys(),
        notation = buffer.notation,
        mode = mode,
        rpn = StoredRpn(
            stack = rpn.stack.keys(),
            entry = rpn.entryKeys(),
            entryNegative = rpn.entryNegative,
        ),
        simple = StoredSimple(
            entry = simple.entryKeys(),
            accumulator = simple.accumulatorKeys(),
            pending = simple.pendingKeys(),
            entryNegative = simple.entryNegative,
        ),
        showingResult = showingResult,
        decimalApproximate = decimalApproximate,
    )

    private fun restore(session: StoredSession) {
        buffer = ExpressionBuffer.replay(session.keys, session.notation)
        rpn = RpnSession.restore(
            stackKeys = session.rpn.stack,
            entryKeys = session.rpn.entry,
            entryNegative = session.rpn.entryNegative,
            notation = session.notation,
        )
        simple = SimpleSession.restore(
            entryKeys = session.simple.entry,
            accumulatorKeys = session.simple.accumulator,
            pendingKeys = session.simple.pending,
            entryNegative = session.simple.entryNegative,
            notation = session.notation,
        )
        mode = session.mode
        // Persistés : sans eux, un chiffre tapé après `2+3=` et une mort du processus
        // prolongeait le résultat au lieu de repartir de zéro.
        showingResult = session.showingResult
        decimalApproximate = session.decimalApproximate
        error = null
        publish()
    }

    private fun saveInstanceState(session: StoredSession) {
        savedStateHandle[STATE_KEYS] = session.keys
        savedStateHandle[STATE_NOTATION] = session.notation.name
        savedStateHandle[STATE_MODE] = session.mode.name
        savedStateHandle[STATE_RPN_STACK] = session.rpn.stack
        savedStateHandle[STATE_RPN_ENTRY] = session.rpn.entry
        savedStateHandle[STATE_RPN_NEGATIVE] = session.rpn.entryNegative
        savedStateHandle[STATE_SIMPLE_ENTRY] = session.simple.entry
        savedStateHandle[STATE_SIMPLE_ACCUMULATOR] = session.simple.accumulator
        savedStateHandle[STATE_SIMPLE_PENDING] = session.simple.pending
        savedStateHandle[STATE_SIMPLE_NEGATIVE] = session.simple.entryNegative
        savedStateHandle[STATE_SHOWING_RESULT] = session.showingResult
        savedStateHandle[STATE_DECIMAL_APPROXIMATE] = session.decimalApproximate
    }

    /** L'état sauvé par le système, ou `null` s'il s'agit d'un lancement à froid. */
    private fun savedInstanceSession(): StoredSession? {
        val keys = savedStateHandle.get<String>(STATE_KEYS) ?: return null
        return StoredSession(
            keys = keys,
            notation = enumNamed(savedStateHandle[STATE_NOTATION], NumberNotation.DECIMAL),
            mode = enumNamed(savedStateHandle[STATE_MODE], CalculationMode.CLASSIC),
            rpn = StoredRpn(
                stack = savedStateHandle.get<String>(STATE_RPN_STACK).orEmpty(),
                entry = savedStateHandle.get<String>(STATE_RPN_ENTRY).orEmpty(),
                entryNegative = savedStateHandle.get<Boolean>(STATE_RPN_NEGATIVE) == true,
            ),
            simple = StoredSimple(
                entry = savedStateHandle.get<String>(STATE_SIMPLE_ENTRY).orEmpty(),
                accumulator = savedStateHandle.get<String>(STATE_SIMPLE_ACCUMULATOR).orEmpty(),
                pending = savedStateHandle.get<String>(STATE_SIMPLE_PENDING).orEmpty(),
                entryNegative = savedStateHandle.get<Boolean>(STATE_SIMPLE_NEGATIVE) == true,
            ),
            showingResult = savedStateHandle.get<Boolean>(STATE_SHOWING_RESULT) == true,
            decimalApproximate = savedStateHandle.get<Boolean>(STATE_DECIMAL_APPROXIMATE) == true,
        )
    }

    /** Les trois écritures de la ligne principale, rendues d'un seul coup. */
    private data class Lines(
        val decimal: Rendered,
        val glyphs: Rendered,
        val labels: Rendered,
        /** Jamais affichée : elle n'existe que pour être copiée. */
        val base4: Rendered,
    )

    companion object {
        private const val STATE_KEYS = "expression-keys"
        private const val STATE_NOTATION = "expression-notation"
        private const val STATE_MODE = "calculation-mode"
        private const val STATE_RPN_STACK = "rpn-stack"
        private const val STATE_RPN_ENTRY = "rpn-entry"
        private const val STATE_RPN_NEGATIVE = "rpn-entry-negative"
        private const val STATE_SIMPLE_ENTRY = "simple-entry"
        private const val STATE_SIMPLE_ACCUMULATOR = "simple-accumulator"
        private const val STATE_SIMPLE_PENDING = "simple-pending"
        private const val STATE_SIMPLE_NEGATIVE = "simple-entry-negative"
        private const val STATE_SHOWING_RESULT = "showing-result"
        private const val STATE_DECIMAL_APPROXIMATE = "decimal-approximate"
        private const val PERSIST_DEBOUNCE_MS = 300L

        /**
         * Injection par constructeur, sans framework de DI : quatre modules et un seul
         * ViewModel ne justifient pas Hilt.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    CalculatorViewModel(
                        sessionStore = CalculatorPreferences(applicationContext),
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
        }
    }
}

/** La constante nommée [name], ou [default] si le nom est absent ou inconnu. */
private inline fun <reified E : Enum<E>> enumNamed(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
