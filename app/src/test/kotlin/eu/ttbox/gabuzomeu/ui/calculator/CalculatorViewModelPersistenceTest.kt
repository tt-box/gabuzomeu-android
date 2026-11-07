package eu.ttbox.gabuzomeu.ui.calculator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.data.SessionStore
import eu.ttbox.gabuzomeu.data.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Persistance de la session : ce qui doit survivre à une mort du processus, à un retour
 * arrière pressé, et à une lecture disque encore en cours au premier geste.
 *
 * Séparé de [CalculatorViewModelTest], devenu trop long pour Detekt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelPersistenceTest {

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

    private fun CalculatorViewModel.twoPlusThreeEquals() {
        onKey(KeyAction.Digit('2'))
        onKey(KeyAction.Op(Operator.PLUS))
        onKey(KeyAction.Digit('3'))
        onKey(KeyAction.Evaluate)
    }

    @Test
    fun `un chiffre apres un resultat repart de zero meme apres mort du processus`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).twoPlusThreeEquals()

        // Nouveau ViewModel, meme SavedStateHandle : le resultat doit rester un resultat.
        val restored = viewModel(savedState)
        assertTrue(restored.uiState.value.showingResult)

        restored.onKey(KeyAction.Digit('7'))

        // Et non « 57 » : le drapeau de resultat n'etait pas sauvegarde.
        assertEquals("7", restored.uiState.value.decimal)
    }

    @Test
    fun `l'etat de resultat est persiste sur le disque et restaure a froid`() = runTest {
        viewModel().twoPlusThreeEquals()
        advanceUntilIdle()
        assertTrue(store.saved.value.showingResult)

        val restored = viewModel()
        advanceUntilIdle()
        restored.onKey(KeyAction.Digit('7'))

        assertEquals("7", restored.uiState.value.decimal)
    }

    @Test
    fun `une session ancienne sans drapeau de resultat reste une saisie`() = runTest {
        // Les valeurs par defaut de StoredSession sont celles des sessions ecrites avant
        // l'apparition du drapeau.
        store.saved.value = StoredSession(keys = "5")

        val model = viewModel()
        advanceUntilIdle()
        model.onKey(KeyAction.Digit('7'))

        assertEquals("57", model.uiState.value.decimal)
    }

    @Test
    fun `quitter l'ecran avant l'anti-rebond n'egare pas la derniere frappe`() = runTest {
        val viewModelStore = ViewModelStore()
        val factory = viewModelFactory {
            initializer {
                CalculatorViewModel(
                    sessionStore = store,
                    savedStateHandle = SavedStateHandle(),
                    flushScope = this@runTest,
                )
            }
        }
        val model = ViewModelProvider.create(viewModelStore, factory)[CalculatorViewModel::class]
        // La lecture disque initiale se termine, puis l'utilisateur tape et sort aussitot.
        advanceUntilIdle()
        model.onKey(KeyAction.Digit('4'))
        model.onKey(KeyAction.Digit('2'))
        advanceTimeBy(PERSIST_DELAY_BEFORE_DEBOUNCE_MS)

        // Retour arriere : viewModelScope est annule avant la fin de l'anti-rebond.
        viewModelStore.clear()
        advanceUntilIdle()

        assertEquals("42", store.saved.value.keys)
    }

    @Test
    fun `un mode choisi pendant la lecture disque n'est pas ecrase`() = runTest {
        store.saved.value = StoredSession(keys = "12", mode = CalculationMode.CLASSIC)

        val model = viewModel()
        // L'utilisateur bascule en NPI avant que la lecture asynchrone ne se termine.
        model.onModeChange(CalculationMode.RPN)
        advanceUntilIdle()

        assertEquals(CalculationMode.RPN, model.uiState.value.mode)
        // Le reste de la session est bien restaure : l'expression classique est la.
        model.onModeChange(CalculationMode.CLASSIC)
        assertEquals("12", model.uiState.value.decimal)
    }

    @Test
    fun `une notation choisie pendant la lecture disque n'est pas ecrasee`() = runTest {
        store.saved.value = StoredSession(keys = "12", notation = NumberNotation.DECIMAL)

        val model = viewModel()
        model.onNotationChange(NumberNotation.SHADOK)
        advanceUntilIdle()

        assertEquals(NumberNotation.SHADOK, model.uiState.value.notation)
        assertEquals("12", model.uiState.value.decimal)
        assertEquals(NumberNotation.SHADOK, store.saved.value.notation)
    }

    @Test
    fun `la session vide du demarrage n'ecrase pas le disque avant sa lecture`() = runTest {
        store.saved.value = StoredSession(keys = "12")

        viewModel()
        advanceUntilIdle()

        assertEquals("12", store.saved.value.keys)
        // Rien n'a change : inutile de reecrire ce qui vient d'etre lu.
        assertEquals(0, store.saveCount)
    }

    // ------------------------------------------------------------ valeurs exactes

    private fun CalculatorViewModel.oneThirdEquals() {
        onKey(KeyAction.Digit('1'))
        onKey(KeyAction.Op(Operator.DIVIDE))
        onKey(KeyAction.Digit('3'))
        onKey(KeyAction.Evaluate)
    }

    /** `×3=` sur la valeur restaurée : 1 si le tiers est exact, 0.99999… s'il est tronqué. */
    private fun CalculatorViewModel.timesThreeEquals(): String {
        onKey(KeyAction.Op(Operator.TIMES))
        onKey(KeyAction.Digit('3'))
        onKey(KeyAction.Evaluate)
        return uiState.value.decimal
    }

    @Test
    fun `un tiers repris d'un resultat reste exact apres mort du processus`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).oneThirdEquals()

        assertEquals("1", viewModel(savedState).timesThreeEquals())
    }

    @Test
    fun `un tiers repris d'un resultat reste exact apres un redemarrage a froid`() = runTest {
        viewModel().oneThirdEquals()
        advanceUntilIdle()
        assertEquals("1/3", store.saved.value.exact)

        val restored = viewModel()
        advanceUntilIdle()

        assertEquals("1", restored.timesThreeEquals())
    }

    @Test
    fun `un collage inexact en NPI et en Simple survit a la mort du processus`() = runTest {
        listOf(CalculationMode.RPN, CalculationMode.SIMPLE).forEach { mode ->
            val savedState = SavedStateHandle()
            viewModel(savedState).apply {
                onModeChange(mode)
                onNotationChange(NumberNotation.SHADOK)
                onPaste("0.7")
            }

            val restored = viewModel(savedState)
            restored.onNotationChange(NumberNotation.DECIMAL)

            assertEquals("0.7", restored.uiState.value.decimal, "en $mode")
        }
    }

    @Test
    fun `une session ancienne sans valeurs exactes se charge depuis ses chiffres`() = runTest {
        store.saved.value = StoredSession(keys = "0.5", showingResult = true)

        val model = viewModel()
        advanceUntilIdle()

        assertEquals("0.5", model.uiState.value.decimal)
        assertEquals("1.5", model.timesThreeEquals())
    }

    @Test
    fun `une valeur exacte persistee invalide est ignoree`() = runTest {
        // Dénominateur nul : le nombre repart de ses chiffres, sans planter.
        store.saved.value = StoredSession(keys = "0.5", exact = "1/0")

        val model = viewModel()
        advanceUntilIdle()

        assertEquals("1.5", model.timesThreeEquals())
    }

    private companion object {
        /** Moins que les 300 ms de l'anti-rebond : l'ecriture n'est pas encore partie. */
        const val PERSIST_DELAY_BEFORE_DEBOUNCE_MS = 100L
    }

    private class FakeSessionStore : SessionStore {
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
