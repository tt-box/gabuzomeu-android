package eu.ttbox.gabuzomeu.data

import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import kotlinx.coroutines.flow.Flow

/**
 * L'état de la calculatrice en NPI, sous sa forme stockable.
 *
 * La pile est écrite en **fractions** (`"1/3;-7"`) et non en décimales : c'est ce qui fait
 * qu'un tiers empilé se retrouve tel quel après une mort du processus, au lieu de revenir
 * arrondi à vingt décimales.
 */
data class StoredRpn(
    val stack: String = "",
    val entry: String = "",
    val entryNegative: Boolean = false,
)

/**
 * L'état de la calculatrice en mode Simple, sous sa forme stockable.
 *
 * L'accumulateur est écrit en **fraction** (`"1/3"`) pour la même raison que la pile NPI :
 * un tiers obtenu par division se retrouve exact au redémarrage, et non arrondi à vingt
 * décimales. L'opération en attente est réduite à son symbole.
 */
data class StoredSimple(
    val entry: String = "",
    val accumulator: String = "",
    val pending: String = "",
    val entryNegative: Boolean = false,
)

/**
 * La dernière session, telle que restaurée au lancement.
 *
 * Les trois modes de calcul sont conservés **côte à côte** : basculer de la calculatrice
 * classique vers la NPI ne détruit rien, et revenir retrouve l'expression laissée en
 * chemin. [mode] dit seulement lequel des trois est affiché.
 *
 * [showingResult] et [decimalApproximate] décrivent l'expression du mode classique : sans
 * eux, un `7` tapé après `2+3=` et une mort du processus prolongeait le résultat (« 57 »)
 * au lieu de repartir de zéro. Leurs valeurs par défaut sont celles d'une saisie en cours,
 * ce qui garde lisibles les sessions écrites avant leur apparition.
 */
data class StoredSession(
    val keys: String = "",
    val notation: NumberNotation = NumberNotation.DECIMAL,
    val mode: CalculationMode = CalculationMode.CLASSIC,
    val rpn: StoredRpn = StoredRpn(),
    val simple: StoredSimple = StoredSimple(),
    val showingResult: Boolean = false,
    val decimalApproximate: Boolean = false,
)

/**
 * Ce que l'utilisateur choisit d'afficher.
 *
 * La ligne de **glyphes** Shadok n'est pas configurable : c'est l'identité de
 * l'application et la seule écriture toujours présente. Les deux autres lignes — les
 * noms prononcés et la traduction décimale — se masquent à volonté.
 */
data class DisplaySettings(val showShadokLabels: Boolean = true, val showDecimal: Boolean = true)

/**
 * Persistance de la session et des préférences d'affichage.
 *
 * L'interface existe pour que [eu.ttbox.gabuzomeu.ui.calculator.CalculatorViewModel] ne
 * dépende pas d'un `Context` : ses tests tournent ainsi sur la JVM, sans Robolectric ni
 * émulateur.
 */
interface SessionStore {
    val session: Flow<StoredSession>
    val settings: Flow<DisplaySettings>

    /**
     * Écrit la session entière — les trois modes d'un coup.
     *
     * Une seule méthode plutôt qu'une par morceau d'état : l'anti-rebond du ViewModel
     * n'a ainsi qu'un flux à regrouper, et il est impossible d'écrire un mode en oubliant
     * l'autre.
     */
    suspend fun save(session: StoredSession)

    suspend fun saveSettings(settings: DisplaySettings)
}
