package eu.ttbox.gabuzomeu.ui.calculator

import androidx.lifecycle.SavedStateHandle
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.data.SessionStore
import eu.ttbox.gabuzomeu.data.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les branches rares du ViewModel : états sauvegardés anciens, reprise après un résultat en
 * mode classique, touches de pile de la NPI, écriture finale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelBranchesTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: RecordingStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = RecordingStore()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        flushScope: CoroutineScope? = null,
    ) = if (flushScope == null) {
        CalculatorViewModel(sessionStore = store, savedStateHandle = savedState)
    } else {
        CalculatorViewModel(store, savedState, flushScope)
    }

    private fun CalculatorViewModel.press(vararg actions: KeyAction) = actions.forEach(::onKey)

    private fun digit(c: Char) = KeyAction.Digit(c)

    private fun CalculatorViewModel.twoPlusThreeEquals() = press(
        digit('2'),
        KeyAction.Op(Operator.PLUS),
        digit('3'),
        KeyAction.Evaluate,
    )

    // ------------------------------------------------------ états sauvegardés anciens

    @Test
    fun `un etat sauvegarde sans autres champs reprend les valeurs par defaut`() = runTest {
        val model = viewModel(SavedStateHandle(mapOf("expression-keys" to "7")))

        val state = model.uiState.value
        assertEquals(NumberNotation.DECIMAL, state.notation)
        assertEquals(CalculationMode.CLASSIC, state.mode)
        assertEquals("7", state.decimal)
        assertFalse(state.showingResult)
        assertTrue(state.stack.isEmpty())
    }

    @Test
    fun `des noms d'enumeration inconnus retombent sur les defauts`() = runTest {
        val model = viewModel(
            SavedStateHandle(
                mapOf(
                    "expression-keys" to "7",
                    "expression-notation" to "HEXADECIMAL",
                    "calculation-mode" to "SCIENTIFIC",
                ),
            ),
        )

        assertEquals(NumberNotation.DECIMAL, model.uiState.value.notation)
        assertEquals(CalculationMode.CLASSIC, model.uiState.value.mode)
    }

    @Test
    fun `l'etat sauvegarde ne cree pas de valeurs exactes`() = runTest {
        val model = viewModel(SavedStateHandle(mapOf("expression-keys" to "1")))
        advanceUntilIdle()

        assertEquals("", store.saved.value.exact)
        assertEquals("", store.saved.value.rpn.entryExact)
        assertEquals("", store.saved.value.simple.entryExact)
        assertEquals("1", model.uiState.value.decimal)
    }

    // ------------------------------------------- mode classique, après un résultat

    @Test
    fun `le separateur apres un resultat repart de zero`() = runTest {
        val model = viewModel()
        model.twoPlusThreeEquals()

        model.press(KeyAction.Separator)

        assertFalse(model.uiState.value.showingResult)
        assertTrue(model.uiState.value.decimal.startsWith("0"), model.uiState.value.decimal)
    }

    @Test
    fun `la parenthese ouvrante apres un resultat repart de zero`() = runTest {
        val model = viewModel()
        model.twoPlusThreeEquals()

        model.press(KeyAction.LeftParen)

        assertFalse(model.uiState.value.showingResult)
        assertEquals("(", model.uiState.value.decimal)
    }

    @Test
    fun `la parenthese fermante apres un resultat prolonge le resultat`() = runTest {
        val model = viewModel()
        model.twoPlusThreeEquals()

        model.press(KeyAction.RightParen)

        assertFalse(model.uiState.value.showingResult)
        assertTrue(model.uiState.value.decimal.startsWith("5"), model.uiState.value.decimal)
    }

    @Test
    fun `effacer apres un resultat prolonge le resultat au lieu de repartir`() = runTest {
        val model = viewModel()
        model.press(digit('1'), digit('2'), KeyAction.Op(Operator.PLUS), digit('3'))
        model.press(KeyAction.Evaluate)
        assertEquals("15", model.uiState.value.decimal)

        model.press(KeyAction.Delete)

        assertFalse(model.uiState.value.showingResult)
        assertEquals("1", model.uiState.value.decimal)
    }

    // ------------------------------------------------------------------- mode NPI

    private fun rpnModel(): CalculatorViewModel = viewModel().apply {
        onModeChange(CalculationMode.RPN)
    }

    @Test
    fun `en NPI echanger inverse les deux valeurs du sommet`() = runTest {
        val model = rpnModel()
        model.press(digit('1'), KeyAction.Enter, digit('2'), KeyAction.Enter)

        model.press(KeyAction.Swap)

        assertEquals("1", model.uiState.value.decimal)
        assertEquals("2", model.uiState.value.stack.first().decimal)
    }

    @Test
    fun `en NPI echanger sur une pile trop courte remonte une erreur`() = runTest {
        val model = rpnModel()
        model.press(digit('1'), KeyAction.Enter)

        model.press(KeyAction.Swap)

        assertTrue(model.uiState.value.error != null)
    }

    @Test
    fun `en NPI x depile le sommet`() = runTest {
        val model = rpnModel()
        model.press(digit('1'), KeyAction.Enter, digit('2'), KeyAction.Enter)

        model.press(KeyAction.Drop)

        assertEquals("1", model.uiState.value.decimal)
    }

    @Test
    fun `en NPI le signe s'applique au sommet de la pile`() = runTest {
        val model = rpnModel()
        model.press(digit('5'), KeyAction.Enter, KeyAction.Negate)

        assertEquals("-5", model.uiState.value.decimal)
    }

    @Test
    fun `en NPI effacer retire un chiffre de la frappe`() = runTest {
        val model = rpnModel()
        model.press(digit('1'), digit('2'), KeyAction.Delete)

        assertEquals("1", model.uiState.value.decimal)
    }

    @Test
    fun `en NPI tout effacer vide la pile`() = runTest {
        val model = rpnModel()
        model.press(digit('1'), KeyAction.Enter, digit('2'), KeyAction.Enter)

        model.press(KeyAction.Clear)

        assertTrue(model.uiState.value.isEmpty)
        assertTrue(model.uiState.value.stack.isEmpty())
    }

    // ------------------------------------------------------------- écriture finale

    private fun CalculatorViewModel.clear() {
        val onCleared = CalculatorViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(this)
    }

    @Test
    fun `a la destruction la frappe en attente est ecrite sans attendre l'anti-rebond`() = runTest {
        val flushScope = CoroutineScope(UnconfinedTestDispatcher())
        val model = viewModel(flushScope = flushScope)
        advanceUntilIdle()
        val before = store.saveCount

        model.press(digit('4'))
        model.clear()

        assertEquals(before + 1, store.saveCount)
        assertEquals("4", store.saved.value.keys)
    }

    @Test
    fun `a la destruction rien n'est ecrit quand rien n'est en attente`() = runTest {
        val flushScope = CoroutineScope(UnconfinedTestDispatcher())
        val model = viewModel(flushScope = flushScope)
        model.press(digit('4'))
        advanceUntilIdle()
        val before = store.saveCount

        model.clear()

        assertEquals(before, store.saveCount)
    }

    private class RecordingStore : SessionStore {
        val saved = MutableStateFlow(StoredSession())
        var saveCount: Int = 0
            private set

        override val session: Flow<StoredSession> = saved
        override val settings: Flow<DisplaySettings> = MutableStateFlow(DisplaySettings())

        override suspend fun save(session: StoredSession) {
            saveCount++
            saved.value = session
        }

        override suspend fun saveSettings(settings: DisplaySettings) = Unit
    }
}
