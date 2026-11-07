package eu.ttbox.gabuzomeu.ui.calculator

import androidx.lifecycle.SavedStateHandle
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.data.SessionStore
import eu.ttbox.gabuzomeu.data.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Collage d'un nombre que le pavé actif ne sait pas écrire exactement.
 *
 * Le défaut corrigé : le collage rejouait les touches de l'écriture arrondie. 0.7 décimal
 * collé sur le pavé Shadok — pas d'écriture finie en base 4 — devenait des chiffres
 * tronqués, sans `≈`, et rebasculer en décimal ne redonnait pas 0.7.
 *
 * Séparé de [CalculatorViewModelTest], devenu trop long pour Detekt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelPasteTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CalculatorViewModel(
        sessionStore = InertSessionStore(),
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `un decimal colle en Shadok garde sa valeur exacte dans les trois modes`() = runTest {
        CalculationMode.entries.forEach { mode ->
            val model = viewModel()
            model.onModeChange(mode)
            model.onNotationChange(NumberNotation.SHADOK)

            model.onPaste("0.7")

            assertTrue(model.uiState.value.shadokApproximate, "≈ en $mode")
            assertEquals("0.7", model.uiState.value.decimal, "décimal en $mode")
            model.onNotationChange(NumberNotation.DECIMAL)
            assertEquals("0.7", model.uiState.value.decimal, "retour en décimal en $mode")
        }
    }

    @Test
    fun `un decimal colle en Shadok s'evalue a sa valeur exacte`() = runTest {
        val model = viewModel()
        model.onNotationChange(NumberNotation.SHADOK)
        model.onPaste("0.7")
        model.onKey(KeyAction.Op(Operator.TIMES))
        model.onKey(KeyAction.Digit(ShadokDigit.ZO.glyph))
        model.onKey(KeyAction.Evaluate)

        // 0.7 × 2 = 1.4 exactement, et non le double d'une base 4 tronquée.
        assertEquals("1.4", model.uiState.value.decimal)
        assertFalse(model.uiState.value.decimalApproximate)
    }

    @Test
    fun `un collage negatif inexact garde son signe et sa valeur`() = runTest {
        val model = viewModel()
        model.onModeChange(CalculationMode.RPN)
        model.onNotationChange(NumberNotation.SHADOK)

        model.onPaste("−0.7")
        model.onNotationChange(NumberNotation.DECIMAL)

        assertEquals("−0.7", model.uiState.value.decimal)
    }

    @Test
    fun `un collage exact en Shadok reste une frappe ordinaire`() = runTest {
        // 0.25 s'écrit exactement en base 4 : pas de ≈, et les chiffres suffisent.
        val model = viewModel()
        model.onNotationChange(NumberNotation.SHADOK)

        model.onPaste("0.25")

        assertFalse(model.uiState.value.shadokApproximate)
        assertEquals("0.25", model.uiState.value.decimal)
    }

    /** Un stockage sans souvenir : seuls les gestes du test comptent ici. */
    private class InertSessionStore : SessionStore {
        override val session: Flow<StoredSession> = MutableStateFlow(StoredSession())
        override val settings: Flow<DisplaySettings> = MutableStateFlow(DisplaySettings())
        override suspend fun save(session: StoredSession) = Unit
        override suspend fun saveSettings(settings: DisplaySettings) = Unit
    }
}
