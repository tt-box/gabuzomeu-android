package eu.ttbox.gabuzomeu.ui.calculator

import androidx.lifecycle.SavedStateHandle
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.EvalError
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.data.SessionStore
import eu.ttbox.gabuzomeu.data.StoredRpn
import eu.ttbox.gabuzomeu.data.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests du ViewModel sur la JVM — ni émulateur, ni Robolectric.
 *
 * C'est possible parce que le ViewModel dépend de l'interface [SessionStore] et non d'un
 * `Context`. Dans le projet d'origine, la moindre vérification de ce comportement
 * exigeait un appareil : `Calculator` héritait de `FragmentActivity` et la persistance
 * appelait directement `Context.openFileInput`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeSessionStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeSessionStore()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        CalculatorViewModel(sessionStore = store, savedStateHandle = savedState)

    // ------------------------------------------------------- projections simultanées

    @Test
    fun `les trois affichages sont produits d'un seul coup`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('6'))

        val state = model.uiState.value
        assertEquals("6", state.decimal)
        assertEquals("_⅃", state.glyphs)
        assertEquals("BuZo", state.labels)
        assertFalse(state.shadokApproximate)
    }

    @Test
    fun `une decimale non representable en base 4 est signalee`() = runTest {
        val model = viewModel()

        "0.1".forEach { key ->
            if (key == '.') model.onKey(KeyAction.Separator) else model.onKey(KeyAction.Digit(key))
        }

        assertTrue(model.uiState.value.shadokApproximate)
    }

    // ---------------------------------------------------------------- évaluation

    @Test
    fun `evaluer remplace l'expression par son resultat`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit('7'))
        model.onKey(KeyAction.Evaluate)

        val state = model.uiState.value
        assertEquals("42", state.decimal)
        // 42 en base 4 = 222, soit ZoZoZo.
        assertEquals("ZoZoZo", state.labels)
        assertTrue(state.showingResult)
    }

    @Test
    fun `un resultat sans ecriture decimale finie est signale`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)

        assertTrue(model.uiState.value.decimalApproximate, "1/3 n'a pas d'ecriture decimale finie")
    }

    @Test
    fun `un resultat prolonge garde sa valeur exacte`() = runTest {
        val model = viewModel()

        // 1÷3 = puis ×3 = : le tiers repris dans le tampon doit rester un tiers, pas son
        // arrondi a 20 decimales, sinon on obtient 0.99999999999999999999 sans « ≈ ».
        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)
        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)

        assertEquals("1", model.uiState.value.decimal)
        assertFalse(model.uiState.value.decimalApproximate)
    }

    @Test
    fun `la division par zero remonte une erreur et laisse l'expression intacte`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('0'))
        model.onKey(KeyAction.Evaluate)

        assertEquals(EvalError.DIVISION_BY_ZERO, model.uiState.value.error)
        assertEquals("5÷0", model.uiState.value.decimal)
    }

    @Test
    fun `toute nouvelle frappe acquitte l'erreur`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('0'))
        model.onKey(KeyAction.Evaluate)
        assertEquals(EvalError.DIVISION_BY_ZERO, model.uiState.value.error)

        model.onKey(KeyAction.Delete)
        assertNull(model.uiState.value.error)
    }

    // ------------------------------------------------------------------- règle 5

    @Test
    fun `regle 5 - un chiffre apres un resultat repart de zero`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Op(Operator.PLUS))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)
        assertEquals("5", model.uiState.value.decimal)

        model.onKey(KeyAction.Digit('7'))
        assertEquals("7", model.uiState.value.decimal)
        assertFalse(model.uiState.value.showingResult)
    }

    @Test
    fun `regle 5 - un operateur apres un resultat le prolonge`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Op(Operator.PLUS))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)

        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Evaluate)

        assertEquals("10", model.uiState.value.decimal)
    }

    // --------------------------------------------------------- changement de mode

    @Test
    fun `changer de mode convertit l'expression et bascule le pave`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('6'))
        model.onNotationChange(NumberNotation.SHADOK)

        val state = model.uiState.value
        assertEquals(NumberNotation.SHADOK, state.notation)
        assertEquals("6", state.decimal)
        assertEquals("_⅃", state.glyphs)
    }

    @Test
    fun `en mode Shadok seuls les glyphes sont acceptes`() = runTest {
        val model = viewModel()
        model.onNotationChange(NumberNotation.SHADOK)

        model.onKey(KeyAction.Digit('7'))
        assertTrue(model.uiState.value.isEmpty, "un 7 decimal n'a pas de sens en base 4")

        model.onKey(KeyAction.Digit('◿'))
        assertEquals("3", model.uiState.value.decimal)
    }

    // ------------------------------------------------------------- persistance

    @Test
    fun `l'expression est persistee apres l'anti-rebond`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Digit('2'))
        advanceUntilIdle()

        assertEquals("12", store.saved.value.keys)
        assertEquals(NumberNotation.DECIMAL, store.saved.value.notation)
    }

    @Test
    fun `l'anti-rebond ne garde que la derniere frappe d'une rafale`() = runTest {
        val model = viewModel()

        repeat(6) { model.onKey(KeyAction.Digit('1')) }
        advanceUntilIdle()

        assertEquals("111111", store.saved.value.keys)
        // Une seule ecriture disque pour toute la rafale.
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `SavedStateHandle restaure l'etat apres mort du processus`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        first.onKey(KeyAction.Digit('4'))
        first.onKey(KeyAction.Op(Operator.PLUS))
        first.onKey(KeyAction.Digit('2'))

        // Nouveau ViewModel, meme SavedStateHandle : ce que fait le systeme au retour.
        val restored = viewModel(savedState)

        assertEquals("4+2", restored.uiState.value.decimal)
    }

    @Test
    fun `le magasin restaure la derniere session au lancement a froid`() = runTest {
        store.saved.value = StoredSession(keys = "⅃⅃", notation = NumberNotation.SHADOK)

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(NumberNotation.SHADOK, model.uiState.value.notation)
        // ⅃⅃ = 22 en base 4 = 10 en decimal.
        assertEquals("10", model.uiState.value.decimal)
    }

    @Test
    fun `une saisie commencee n'est pas ecrasee par la restauration asynchrone`() = runTest {
        store.saved.value = StoredSession(keys = "99", notation = NumberNotation.DECIMAL)

        val model = viewModel()
        // L'utilisateur tape avant que la lecture disque ne se termine.
        model.onKey(KeyAction.Digit('7'))
        advanceUntilIdle()

        assertEquals("7", model.uiState.value.decimal)
    }

    // ------------------------------------------------------- réglages d'affichage

    @Test
    fun `les reglages par defaut affichent les trois lignes`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        val settings = model.uiState.value.settings
        assertTrue(settings.showShadokLabels)
        assertTrue(settings.showDecimal)
    }

    @Test
    fun `masquer les noms Shadok est publie et persiste`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        model.onSettingsChange(DisplaySettings(showShadokLabels = false))
        advanceUntilIdle()

        assertFalse(model.uiState.value.settings.showShadokLabels)
        assertTrue(model.uiState.value.settings.showDecimal)
        assertFalse(store.savedSettings.value.showShadokLabels)
    }

    @Test
    fun `masquer le decimal est publie et persiste`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        model.onSettingsChange(DisplaySettings(showDecimal = false))
        advanceUntilIdle()

        assertFalse(model.uiState.value.settings.showDecimal)
        assertFalse(store.savedSettings.value.showDecimal)
    }

    @Test
    fun `les reglages stockes sont restaures au lancement`() = runTest {
        store.savedSettings.value = DisplaySettings(showShadokLabels = false, showDecimal = false)

        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.uiState.value.settings.showShadokLabels)
        assertFalse(model.uiState.value.settings.showDecimal)
    }

    // ------------------------------------------------------- notation polonaise inverse

    /** `6 ENTER 7 ×` : la NPI n'a ni parenthèses ni « = ». */
    private fun CalculatorViewModel.rpnSixTimesSeven() {
        onModeChange(CalculationMode.RPN)
        onKey(KeyAction.Digit('6'))
        onKey(KeyAction.Enter)
        onKey(KeyAction.Digit('7'))
        onKey(KeyAction.Op(Operator.TIMES))
    }

    @Test
    fun `en NPI le resultat s'affiche sans passer par un egale`() = runTest {
        val model = viewModel()

        model.rpnSixTimesSeven()

        val state = model.uiState.value
        assertEquals(CalculationMode.RPN, state.mode)
        assertEquals("42", state.decimal)
        // 42 en base 4 = 222.
        assertEquals("ZoZoZo", state.labels)
        // Le resultat est X : il ne reste rien sous lui.
        assertTrue(state.stack.isEmpty())
    }

    @Test
    fun `la pile publiee ne montre que les niveaux sous X`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('7'))

        val state = model.uiState.value
        assertEquals("7", state.decimal, "X est la frappe en cours")
        assertEquals(listOf("2"), state.stack.map { it.decimal })
        // Chaque niveau porte aussi ses noms Shadok, pour TalkBack.
        assertEquals("Zo", state.stack.single().labels)
    }

    /**
     * Un seul ENTER empile — le moteur n'a jamais fait autrement. Ce que l'état publie en
     * plus, c'est de quel côté du séparateur la valeur se trouve : sans ce drapeau,
     * l'afficheur dessinait « 6 tapé » et « 6 empilé » de la même façon, on appuyait deux
     * fois, et la duplication du sommet laissait un doublon dans la pile.
     */
    @Test
    fun `l'etat dit si la valeur est encore sous le doigt ou deja empilee`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('6'))
        assertTrue(model.uiState.value.entering, "6 est une frappe, pas un niveau de pile")

        model.onKey(KeyAction.Enter)

        val state = model.uiState.value
        assertFalse(state.entering, "un seul ENTER, et la valeur est empilee")
        assertEquals("6", state.decimal, "elle reste la grande valeur : elle est le sommet")

        // Le second appui duplique le sommet — convention HP, et le piege que l'afficheur
        // tendait a qui ne voyait pas le premier ENTER.
        model.onKey(KeyAction.Enter)
        assertEquals(listOf("6"), model.uiState.value.stack.map { it.decimal })
    }

    @Test
    fun `la notion de frappe en cours n'existe pas en mode classique`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('6'))

        assertFalse(model.uiState.value.entering, "une expression infixe ne s'empile pas")
    }

    @Test
    fun `en NPI un operateur sur une pile trop courte remonte une erreur`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))

        assertEquals(EvalError.STACK_UNDERFLOW, model.uiState.value.error)
        // La frappe est intacte : l'utilisateur corrige plutot que de tout retaper.
        assertEquals("5", model.uiState.value.decimal)
    }

    @Test
    fun `en NPI la division par zero laisse les deux operandes`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('0'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))

        assertEquals(EvalError.DIVISION_BY_ZERO, model.uiState.value.error)
        assertEquals("0", model.uiState.value.decimal)
        assertEquals(listOf("5"), model.uiState.value.stack.map { it.decimal })
    }

    @Test
    fun `en NPI un tiers est signale comme approche`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))

        val state = model.uiState.value
        assertTrue(state.decimalApproximate, "un tiers n'a pas d'ecriture decimale finie")
        assertTrue(state.shadokApproximate, "ni de developpement fini en base 4")
    }

    @Test
    fun `le plus-ou-moins signe la frappe en NPI`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)

        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Negate)

        assertEquals("−5", model.uiState.value.decimal)
    }

    @Test
    fun `DROP et effacement ont des effets distincts en NPI`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Digit('4'))

        // L'effacement ne touche que la frappe.
        model.onKey(KeyAction.Delete)
        assertEquals("3", model.uiState.value.decimal)
        model.onKey(KeyAction.Delete)
        assertEquals("2", model.uiState.value.decimal, "X redevient le sommet de la pile")

        // DROP, lui, depile.
        model.onKey(KeyAction.Drop)
        assertEquals("", model.uiState.value.decimal)
    }

    @Test
    fun `en NPI les parentheses et l'egale sont sans effet`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('7'))

        model.onKey(KeyAction.LeftParen)
        model.onKey(KeyAction.RightParen)
        model.onKey(KeyAction.Evaluate)

        assertEquals("7", model.uiState.value.decimal)
        assertNull(model.uiState.value.error)
    }

    // --------------------------------------------------------- coexistence des modes

    @Test
    fun `basculer de mode ne detruit ni l'expression ni la pile`() = runTest {
        val model = viewModel()

        // Une expression en classique.
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit('7'))

        // Une pile en NPI.
        model.onModeChange(CalculationMode.RPN)
        assertEquals("", model.uiState.value.decimal, "la NPI part de son propre etat, vierge")
        model.onKey(KeyAction.Digit('4'))
        model.onKey(KeyAction.Enter)

        // Retour en classique : l'expression est toujours la.
        model.onModeChange(CalculationMode.CLASSIC)
        assertEquals("6×7", model.uiState.value.decimal)

        // Et la pile aussi.
        model.onModeChange(CalculationMode.RPN)
        assertEquals("4", model.uiState.value.decimal)
    }

    @Test
    fun `changer de notation convertit les deux modes`() = runTest {
        val model = viewModel()

        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('6'))
        model.onModeChange(CalculationMode.CLASSIC)
        model.onKey(KeyAction.Digit('6'))

        model.onNotationChange(NumberNotation.SHADOK)

        // Le mode affiche est converti…
        assertEquals("_⅃", model.uiState.value.glyphs)
        // …et l'autre aussi, sinon son pave refuserait ses propres chiffres au retour.
        model.onModeChange(CalculationMode.RPN)
        assertEquals(NumberNotation.SHADOK, model.uiState.value.notation)
        assertEquals("_⅃", model.uiState.value.glyphs, "la frappe NPI est convertie aussi")
        model.onKey(KeyAction.Digit('◿'))
        // BuZoMeu, soit 123 en base 4 = 27 : le glyphe Shadok a bien ete accepte.
        assertEquals("27", model.uiState.value.decimal)
    }

    @Test
    fun `les deux modes sont persistes ensemble`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('9'))
        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        advanceUntilIdle()

        val stored = store.saved.value
        assertEquals("9", stored.keys)
        assertEquals(CalculationMode.RPN, stored.mode)
        // La pile est stockee en fractions : un tiers reste un tiers.
        assertEquals("1/3", stored.rpn.stack)
    }

    @Test
    fun `le mode et la pile sont restaures au lancement a froid`() = runTest {
        store.saved.value = StoredSession(
            keys = "8",
            mode = CalculationMode.RPN,
            rpn = StoredRpn(stack = "1/3;5", entry = "12", entryNegative = true),
        )

        val model = viewModel()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(CalculationMode.RPN, state.mode)
        assertEquals("−12", state.decimal, "la frappe et son signe sont restaures")
        assertEquals(listOf("0.33333333333333333333", "5"), state.stack.map { it.decimal })
        // Le tiers restaure est exact, donc signale comme tronque a l'affichage.
        assertTrue(state.stack.first().decimalApproximate)
    }

    @Test
    fun `SavedStateHandle restaure aussi le mode et la pile`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        first.rpnSixTimesSeven()

        val restored = viewModel(savedState)

        assertEquals(CalculationMode.RPN, restored.uiState.value.mode)
        assertEquals("42", restored.uiState.value.decimal)
    }

    // ---------------------------------------------------------------- presse-papiers

    @Test
    fun `la quatrieme ecriture accompagne les trois autres`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('6'))

        // Elle n'est affichée nulle part, mais elle doit être là pour être copiée.
        assertEquals("12", model.uiState.value.base4)
    }

    @Test
    fun `un collage s'insere au milieu d'une expression`() = runTest {
        // La preuve que le collage passe par les règles de frappe, et non par un chemin
        // parallèle : il se comporte comme si l'utilisateur avait tapé le nombre.
        val model = viewModel()
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.TIMES))

        model.onPaste("7")

        assertEquals("6×7", model.uiState.value.decimal)
        model.onKey(KeyAction.Evaluate)
        assertEquals("42", model.uiState.value.decimal)
    }

    @Test
    fun `les quatre ecritures se collent toutes`() = runTest {
        val model = viewModel()

        listOf("_⅃", "BuZo", "6").forEach { text ->
            model.onKey(KeyAction.Clear)
            model.onPaste(text)
            assertEquals("6", model.uiState.value.decimal, "collage de \"$text\"")
        }

        // La base 4 ne vaut 6 que sur le pavé Shadok : c'est la règle d'ambiguïté.
        model.onKey(KeyAction.Clear)
        model.onNotationChange(NumberNotation.SHADOK)
        model.onPaste("12")
        assertEquals("6", model.uiState.value.decimal)
    }

    @Test
    fun `un collage apres un resultat repart de zero`() = runTest {
        // Règle 5, identique à un chiffre tapé : le résultat n'est pas un préfixe.
        val model = viewModel()
        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Op(Operator.PLUS))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)
        assertEquals("5", model.uiState.value.decimal)

        model.onPaste("7")

        assertEquals("7", model.uiState.value.decimal)
    }

    @Test
    fun `un collage negatif arrive signe dans les deux modes`() = runTest {
        val model = viewModel()

        model.onPaste("−6")
        assertEquals("−6", model.uiState.value.decimal)

        model.onModeChange(CalculationMode.RPN)
        model.onPaste("−6")
        assertEquals("−6", model.uiState.value.decimal)
    }

    @Test
    fun `un collage en NPI demarre une frappe sans toucher la pile`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('4'))
        model.onKey(KeyAction.Enter)

        model.onPaste("0.25")

        assertEquals("0.25", model.uiState.value.decimal, "X est la frappe collee")
        assertEquals(listOf("4"), model.uiState.value.stack.map { it.decimal })
    }

    @Test
    fun `un niveau de pile porte aussi sa base 4`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Enter)
        model.onKey(KeyAction.Digit('7'))

        assertEquals(listOf("12"), model.uiState.value.stack.map { it.base4 })
    }

    @Test
    fun `un collage illisible ne change rien et ne signale pas d'erreur`() = runTest {
        // L'interface grise « Coller » dans ce cas : personne ne peut déclencher cet appel
        // par un geste. Le silence est donc la bonne réponse, pas un message d'erreur.
        val model = viewModel()
        model.onKey(KeyAction.Digit('6'))

        listOf("", " ", "abc", "1/3", "1e9", "BuXo").forEach { text ->
            model.onPaste(text)
            assertEquals("6", model.uiState.value.decimal, "collage de \"$text\"")
            assertNull(model.uiState.value.error)
        }
    }

    // ---------------------------------------------------------------- mode Simple

    @Test
    fun `en mode Simple les operations se resolvent au fil de la frappe`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)

        // La meme frappe qu'en mode classique, ou elle donne 7 : c'est la difference entre
        // les deux machines, et c'est le routage vers le bon moteur qui la produit.
        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Op(Operator.PLUS))
        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)

        assertEquals("9", model.uiState.value.decimal)
    }

    @Test
    fun `en mode Simple l'operation en attente est publiee`() = runTest {
        // Sans ce champ, « Bu » et « Bu + » dessineraient exactement la meme image : c'est
        // le defaut corrige pour la NPI, transpose a l'execution immediate.
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)

        model.onKey(KeyAction.Digit('6'))
        assertNull(model.uiState.value.pending, "rien n'attend encore")

        model.onKey(KeyAction.Op(Operator.PLUS))
        assertEquals(Operator.PLUS, model.uiState.value.pending)
        assertEquals("6", model.uiState.value.decimal, "la valeur, elle, n'a pas bouge")

        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Evaluate)
        assertNull(model.uiState.value.pending, "le = a resolu l'attente")
        assertEquals("7", model.uiState.value.decimal)
    }

    @Test
    fun `en mode Simple la division par zero laisse tout en place`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)

        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('0'))
        model.onKey(KeyAction.Evaluate)

        assertEquals(EvalError.DIVISION_BY_ZERO, model.uiState.value.error)
        assertEquals("0", model.uiState.value.decimal, "la frappe est intacte")
        assertEquals(Operator.DIVIDE, model.uiState.value.pending)
    }

    @Test
    fun `l'operation en attente n'existe pas hors du mode Simple`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.PLUS))

        model.onModeChange(CalculationMode.CLASSIC)

        assertNull(model.uiState.value.pending)
    }

    @Test
    fun `les trois modes gardent chacun leur etat`() = runTest {
        val model = viewModel()

        model.onKey(KeyAction.Digit('4'))
        model.onKey(KeyAction.Op(Operator.PLUS))

        model.onModeChange(CalculationMode.RPN)
        model.onKey(KeyAction.Digit('5'))
        model.onKey(KeyAction.Enter)

        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.TIMES))

        // Chacun est retrouve exactement ou on l'avait laisse.
        model.onModeChange(CalculationMode.CLASSIC)
        assertEquals("4+", model.uiState.value.decimal)

        model.onModeChange(CalculationMode.RPN)
        assertEquals("5", model.uiState.value.decimal)

        model.onModeChange(CalculationMode.SIMPLE)
        assertEquals("6", model.uiState.value.decimal)
        assertEquals(Operator.TIMES, model.uiState.value.pending)
    }

    @Test
    fun `changer de notation convertit aussi la frappe du mode Simple`() = runTest {
        // L'oublier laisserait le pave Shadok face a une frappe decimale, donc un pave qui
        // refuse ses propres glyphes.
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('6'))

        model.onNotationChange(NumberNotation.SHADOK)

        assertEquals("_⅃", model.uiState.value.glyphs)
    }

    @Test
    fun `un collage en mode Simple demarre une frappe`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('6'))
        model.onKey(KeyAction.Op(Operator.PLUS))

        model.onPaste("0.25")

        assertEquals("0.25", model.uiState.value.decimal)
        assertEquals(Operator.PLUS, model.uiState.value.pending, "l'attente est preservee")
    }

    @Test
    fun `un nombre negatif colle en mode Simple garde son signe`() = runTest {
        // Le pave n'a pas de ±, mais perdre le signe en silence serait pire qu'un etat de
        // plus a afficher.
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('2'))
        model.onKey(KeyAction.Op(Operator.PLUS))

        model.onPaste("−6")
        assertEquals("−6", model.uiState.value.decimal, "le signe se voit pendant la frappe")

        model.onKey(KeyAction.Evaluate)

        // Le resultat vient de Rational.toDecimalString, qui ecrit le moins ASCII, la ou une
        // frappe porte le vrai signe moins U+2212. Ecart d'ecriture deja present en NPI
        // entre la frappe et la pile ; il n'appartient pas a ce mode de le corriger.
        assertEquals("-4", model.uiState.value.decimal)
    }

    @Test
    fun `l'etat du mode Simple survit a un aller-retour de persistance`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.SIMPLE)
        model.onKey(KeyAction.Digit('1'))
        model.onKey(KeyAction.Op(Operator.DIVIDE))
        model.onKey(KeyAction.Digit('3'))
        model.onKey(KeyAction.Evaluate)
        model.onKey(KeyAction.Op(Operator.TIMES))
        advanceUntilIdle()

        // L'accumulateur est ecrit en fraction : c'est ce qui le rend exact au retour.
        assertEquals("1/3", store.saved.value.simple.accumulator)
        assertEquals("×", store.saved.value.simple.pending)

        val restored = viewModel()
        advanceUntilIdle()

        restored.onKey(KeyAction.Digit('3'))
        restored.onKey(KeyAction.Evaluate)
        assertEquals("1", restored.uiState.value.decimal, "trois tiers font exactement un")
    }

    private class FakeSessionStore : SessionStore {
        val saved = MutableStateFlow(StoredSession())
        val savedSettings = MutableStateFlow(DisplaySettings())
        var saveCount: Int = 0
            private set

        override val session: Flow<StoredSession> = saved
        override val settings: Flow<DisplaySettings> = savedSettings

        override suspend fun save(session: StoredSession) {
            saveCount++
            saved.value = session
        }

        override suspend fun saveSettings(settings: DisplaySettings) {
            savedSettings.value = settings
        }
    }
}
