package eu.ttbox.gabuzomeu.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals

/**
 * Un stockage abîmé ne doit jamais faire planter la calculatrice.
 *
 * Le défaut corrigé : sans gestionnaire de corruption ni `catch`, un fichier
 * `calculator.preferences_pb` illisible levait une `CorruptionException` dès la première
 * lecture du ViewModel — donc à chaque lancement.
 */
class CalculatorPreferencesTest {

    @TempDir
    lateinit var directory: File

    @Test
    fun `un fichier corrompu se lit comme une session vide`() = runTest {
        val file = File(directory, "calculator.preferences_pb")
        // Des octets qui ne sont pas un protobuf de préférences valide.
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01, 0x02, 0x03))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = calculatorCorruptionHandler(),
                scope = scope,
                produceFile = { file },
            )
            val store = CalculatorPreferences(dataStore)

            assertEquals(StoredSession(), store.session.first())
            assertEquals(DisplaySettings(), store.settings.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `une erreur de lecture donne les valeurs par défaut`() = runTest {
        val store = CalculatorPreferences(FailingDataStore())

        val session = store.session.first()
        assertEquals("", session.keys)
        assertEquals(NumberNotation.DECIMAL, session.notation)
        assertEquals(DisplaySettings(), store.settings.first())
    }

    @Test
    fun `une erreur d'écriture n'est pas propagée`() = runTest {
        val store = CalculatorPreferences(FailingDataStore())

        store.save(StoredSession(keys = "3"))
        store.saveSettings(DisplaySettings())
    }

    @Test
    fun `une erreur qui n'est pas d'entrée-sortie reste visible`() = runTest {
        val failing = flow<Preferences> { throw IllegalStateException("bogue") }

        assertThrows<IllegalStateException> { failing.orEmptyOnIoError().first() }
    }

    /** Un stockage dont le disque est inutilisable : tout accès lève une `IOException`. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disque illisible") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("disque plein")
    }
}
