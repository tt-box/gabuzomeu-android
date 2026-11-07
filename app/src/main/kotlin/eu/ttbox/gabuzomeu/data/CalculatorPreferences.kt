package eu.ttbox.gabuzomeu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.calculatorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calculator",
    corruptionHandler = calculatorCorruptionHandler(),
)

/**
 * Un fichier illisible est remplacé par des préférences vides.
 *
 * Sans ce gestionnaire, la `CorruptionException` remontait à chaque lecture : le ViewModel la
 * recevait dès son initialisation, et l'application plantait à **chaque** lancement, sans autre
 * issue pour l'utilisateur que d'effacer ses données. Perdre la dernière expression saisie est
 * un prix dérisoire en regard.
 */
internal fun calculatorCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }

/**
 * Une erreur d'entrée-sortie à la lecture se lit comme « rien d'enregistré ».
 *
 * `CorruptionException` est une `IOException` : elle est couverte aussi, au cas où le
 * gestionnaire de corruption lui-même échouerait. Toute autre exception est relancée — ce
 * serait un bogue, pas un incident de stockage.
 */
internal fun Flow<Preferences>.orEmptyOnIoError(): Flow<Preferences> = catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}

/**
 * Implémentation de [SessionStore] sur DataStore Preferences.
 *
 * Remplace `Persist.java` du projet d'origine : un format binaire maison écrit avec
 * `DataOutputStream` dans `calculator.data`, dont les contrôles de version étaient
 * inversés (`Persist.java:55-59` — la branche « version trop récente » était
 * inatteignable) et dont toutes les exceptions étaient avalées par un `log()` lui-même
 * désactivé (`Calculator.LOG_ENABLED = false`).
 *
 * L'historique des calculs n'est pas conservé : la fonctionnalité était déjà morte dans
 * le projet d'origine, `HistoryAdapter` n'étant branché sur aucune `ListView`.
 *
 * Aucune erreur de stockage ne remonte d'ici : lecture comme écriture, un fichier corrompu ou
 * un disque plein se traduisent par une session vide ou une sauvegarde manquée, jamais par un
 * plantage. C'est ce qui permet au ViewModel de lire et d'écrire sans garde.
 */
class CalculatorPreferences internal constructor(private val dataStore: DataStore<Preferences>) :
    SessionStore {

    constructor(context: Context) : this(context.calculatorDataStore)

    private val data: Flow<Preferences> = dataStore.data.orEmptyOnIoError()

    override val session: Flow<StoredSession> = data.map { preferences ->
        StoredSession(
            keys = preferences[KEY_EXPRESSION].orEmpty(),
            notation = preferences[KEY_NOTATION]
                ?.let { stored -> NumberNotation.entries.firstOrNull { it.name == stored } }
                ?: NumberNotation.DECIMAL,
            mode = preferences[KEY_MODE]
                ?.let { stored -> CalculationMode.entries.firstOrNull { it.name == stored } }
                ?: CalculationMode.CLASSIC,
            rpn = StoredRpn(
                stack = preferences[KEY_RPN_STACK].orEmpty(),
                entry = preferences[KEY_RPN_ENTRY].orEmpty(),
                entryNegative = preferences[KEY_RPN_NEGATIVE] == true,
            ),
            simple = StoredSimple(
                entry = preferences[KEY_SIMPLE_ENTRY].orEmpty(),
                accumulator = preferences[KEY_SIMPLE_ACCUMULATOR].orEmpty(),
                pending = preferences[KEY_SIMPLE_PENDING].orEmpty(),
                entryNegative = preferences[KEY_SIMPLE_NEGATIVE] == true,
            ),
        )
    }

    override val settings: Flow<DisplaySettings> = data.map { preferences ->
        val defaults = DisplaySettings()
        DisplaySettings(
            showShadokLabels = preferences[KEY_SHOW_LABELS] ?: defaults.showShadokLabels,
            showDecimal = preferences[KEY_SHOW_DECIMAL] ?: defaults.showDecimal,
        )
    }

    override suspend fun save(session: StoredSession) {
        editIgnoringIoErrors { preferences ->
            preferences[KEY_EXPRESSION] = session.keys
            preferences[KEY_NOTATION] = session.notation.name
            preferences[KEY_MODE] = session.mode.name
            preferences[KEY_RPN_STACK] = session.rpn.stack
            preferences[KEY_RPN_ENTRY] = session.rpn.entry
            preferences[KEY_RPN_NEGATIVE] = session.rpn.entryNegative
            preferences[KEY_SIMPLE_ENTRY] = session.simple.entry
            preferences[KEY_SIMPLE_ACCUMULATOR] = session.simple.accumulator
            preferences[KEY_SIMPLE_PENDING] = session.simple.pending
            preferences[KEY_SIMPLE_NEGATIVE] = session.simple.entryNegative
        }
    }

    override suspend fun saveSettings(settings: DisplaySettings) {
        editIgnoringIoErrors { preferences ->
            preferences[KEY_SHOW_LABELS] = settings.showShadokLabels
            preferences[KEY_SHOW_DECIMAL] = settings.showDecimal
        }
    }

    /**
     * Écrit, sans laisser une erreur d'entrée-sortie (disque plein, fichier verrouillé ou
     * corrompu) remonter jusqu'au ViewModel.
     *
     * Une sauvegarde manquée ne coûte que la reprise de la session au prochain lancement ;
     * une exception non rattrapée, elle, coûterait l'application entière.
     */
    @Suppress("SwallowedException")
    private suspend fun editIgnoringIoErrors(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { preferences -> transform(preferences) }
        } catch (_: IOException) {
            // Volontairement ignorée : voir la documentation ci-dessus.
        }
    }

    private companion object {
        val KEY_EXPRESSION = stringPreferencesKey("expression")
        val KEY_NOTATION = stringPreferencesKey("notation")
        val KEY_MODE = stringPreferencesKey("calculation-mode")
        val KEY_RPN_STACK = stringPreferencesKey("rpn-stack")
        val KEY_RPN_ENTRY = stringPreferencesKey("rpn-entry")
        val KEY_RPN_NEGATIVE = booleanPreferencesKey("rpn-entry-negative")
        val KEY_SIMPLE_ENTRY = stringPreferencesKey("simple-entry")
        val KEY_SIMPLE_ACCUMULATOR = stringPreferencesKey("simple-accumulator")
        val KEY_SIMPLE_PENDING = stringPreferencesKey("simple-pending")
        val KEY_SIMPLE_NEGATIVE = booleanPreferencesKey("simple-entry-negative")
        val KEY_SHOW_LABELS = booleanPreferencesKey("show-shadok-labels")
        val KEY_SHOW_DECIMAL = booleanPreferencesKey("show-decimal")
    }
}
