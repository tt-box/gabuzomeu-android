package eu.ttbox.gabuzomeu.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'écran de réglages d'un widget **déjà posé**, quel qu'en soit le genre.
 *
 * Une seule activité pour les trois widgets : l'arrière-plan se règle partout de la même façon,
 * et seules les options d'heure sont propres à l'horloge numérique. Le genre est déduit du
 * `appWidgetId` reçu, via son provider — plutôt que passé en extra, qui pourrait mentir.
 *
 * Ouvert à la pose, et réouvrable ensuite : les descripteurs déclarent
 * `widgetFeatures="reconfigurable|configuration_optional"`, ce que l'API 31 — notre `minSdk` —
 * permet. `configuration_optional` compte : sans lui, le widget ne serait ajouté qu'après
 * validation de cet écran, et l'annuler ne laisserait rien sur l'écran d'accueil.
 *
 * Les réglages sont écrits dans l'état Glance de **cette instance**. C'est ce qui permet deux
 * widgets réglés différemment côte à côte — l'un opaque, l'autre transparent.
 */
class ClockWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Vrai dès que l'enregistrement est lancé : le bouton est alors grisé. */
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Résultat par défaut : annulé. Le système l'exige — sans RESULT_OK explicite, un
        // widget en cours de pose doit être considéré comme refusé.
        setResult(RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Lancée hors du flux de configuration : il n'y a rien à régler.
            finish()
            return
        }

        val isDigitalClock = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className == ShadokClockWidgetReceiver::class.java.name

        setContent {
            MaterialTheme {
                // Les réglages en vigueur, relus depuis l'état de CETTE instance.
                //
                // Sans cette lecture, l'écran s'ouvrait sur les valeurs par défaut : rouvrir les
                // paramètres d'un widget déjà réglé donnait l'impression d'une remise à zéro, et
                // valider sans y toucher écrasait effectivement les choix précédents.
                var loaded by remember { mutableStateOf<ClockWidgetOptions?>(null) }
                LaunchedEffect(appWidgetId) { loaded = readOptions() }

                loaded?.let { current ->
                    WidgetConfigScreen(
                        initial = current,
                        showClockOptions = isDigitalClock,
                        saving = saving,
                        onSave = ::save,
                    )
                }
            }
        }
    }

    /** L'état persisté de cette instance, ou les défauts si elle n'a jamais été configurée. */
    private suspend fun readOptions(): ClockWidgetOptions {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val preferences = getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
        return ClockWidgetOptions.from(preferences)
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    /**
     * Enregistre puis redessine, avant de rendre la main.
     *
     * Le redessin est indispensable : écrire l'état ne déclenche aucune recomposition par
     * lui-même, et le widget resterait affiché avec ses anciens réglages jusqu'au prochain tic.
     * Les trois genres sont rafraîchis — `updateAll` ne fait rien pour un genre absent.
     *
     * Écriture, redessin et résultat forment un tout, exécuté sous [NonCancellable] :
     * `lifecycleScope` est annulé dès que l'activité est détruite (retour arrière, rotation,
     * processus réclamé). Annulé entre l'écriture et le redessin, le widget gardait son ancien
     * aspect jusqu'au prochain tic ; annulé avant `setResult`, l'hôte recevait RESULT_CANCELED
     * et retirait un widget pourtant réglé. Le bouton est grisé pendant ce temps, ce qui écarte
     * aussi les doubles appuis et les écritures concurrentes.
     */
    private fun save(options: ClockWidgetOptions) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            withContext(NonCancellable) {
                val context = this@ClockWidgetConfigActivity
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { preferences ->
                    ClockWidgetOptions.writeTo(preferences, options)
                }
                ShadokWidgets.all().forEach { widget -> widget.updateAll(context) }

                setResult(RESULT_OK, resultIntent())
            }
            finish()
        }
    }
}

@Composable
private fun WidgetConfigScreen(
    initial: ClockWidgetOptions,
    showClockOptions: Boolean,
    saving: Boolean,
    onSave: (ClockWidgetOptions) -> Unit,
) {
    // `initial` vient de l'état persisté : sur un widget déjà réglé, l'écran s'ouvre donc sur ses
    // choix, et non sur les valeurs par défaut.
    var options by remember { mutableStateOf(initial) }

    Scaffold { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                // Défilable : sur l'horloge, la liste des formats de date déborde d'un écran court.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BackgroundSection(
                background = options.background,
                onChange = { options = options.copy(background = it) },
            )

            if (showClockOptions) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ClockSection(options = options, onChange = { options = it })
            }

            Button(
                onClick = { onSave(options) },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text(text = stringResource(R.string.clock_config_save))
            }
        }
    }
}

/** Les réglages communs aux trois widgets. */
@Composable
private fun BackgroundSection(
    background: WidgetBackgroundOptions,
    onChange: (WidgetBackgroundOptions) -> Unit,
) {
    Text(
        text = stringResource(R.string.config_background),
        style = MaterialTheme.typography.titleMedium,
    )
    SwitchRow(
        label = stringResource(R.string.config_background_visible),
        checked = background.visible,
        onCheckedChange = { onChange(background.copy(visible = it)) },
    )

    if (background.visible) {
        // Masqué quand l'arrière-plan est éteint : régler l'opacité de quelque chose d'invisible
        // n'a pas de sens, et le réglage est conservé pour le rallumage.
        val minOpacity = WidgetBackgroundOptions.FULLY_TRANSPARENT.toFloat()
        val maxOpacity = WidgetBackgroundOptions.FULLY_OPAQUE.toFloat()

        Text(
            text = stringResource(R.string.config_background_opacity, background.opacityPercent),
        )
        Slider(
            value = background.opacityPercent.toFloat(),
            onValueChange = { onChange(background.copy(opacityPercent = it.toInt())) },
            valueRange = minOpacity..maxOpacity,
            // Dix crans de 10 % : assez fin pour doser, assez grossier pour être reproductible
            // au doigt, et l'étiquette au-dessus donne la valeur exacte.
            steps = OPACITY_STEPS,
        )
    }

    Text(
        text = stringResource(R.string.config_background_color),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    WidgetBackgroundTheme.entries.forEach { theme ->
        RadioRow(
            label = stringResource(themeLabelOf(theme)),
            selected = background.theme == theme,
            onSelect = { onChange(background.copy(theme = theme)) },
        )
    }
}

/** Les réglages propres à l'horloge numérique. */
@Composable
private fun ClockSection(options: ClockWidgetOptions, onChange: (ClockWidgetOptions) -> Unit) {
    Text(
        text = stringResource(R.string.clock_config_notation),
        style = MaterialTheme.typography.titleMedium,
    )
    ShadokNotation.entries.forEach { notation ->
        RadioRow(
            label = stringResource(labelOf(notation)),
            preview = ShadokTimeGlyphs.previewOf(notation),
            selected = options.notation == notation,
            onSelect = { onChange(options.copy(notation = notation)) },
        )
    }

    SwitchRow(
        label = stringResource(R.string.clock_config_show_decimal),
        checked = options.showDecimalTime,
        onCheckedChange = { onChange(options.copy(showDecimalTime = it)) },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    Text(
        text = stringResource(R.string.clock_config_date),
        style = MaterialTheme.typography.titleMedium,
    )
    ClockDateFormat.entries.forEach { format ->
        RadioRow(
            label = stringResource(dateLabelOf(format)),
            selected = options.dateFormat == format,
            onSelect = { onChange(options.copy(dateFormat = format)) },
        )
    }

    if (options.dateFormat != ClockDateFormat.NONE) {
        // Sans date affichée, la question de sa position ne se pose pas.
        SwitchRow(
            label = stringResource(R.string.clock_config_date_above),
            checked = options.dateAboveTime,
            onCheckedChange = { onChange(options.copy(dateAboveTime = it)) },
        )
    }
}

/**
 * Un choix exclusif, avec un aperçu facultatif.
 *
 * L'aperçu est un exemple réel — 6 h 6 rendu dans la notation — et non une étiquette abstraite :
 * « BuZo:BuZo » dit immédiatement à quoi ressemblera l'horloge, ce que « Noms » seul ne dit pas.
 */
@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    preview: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
        if (preview != null) {
            Text(
                text = "   $preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun themeLabelOf(theme: WidgetBackgroundTheme): Int = when (theme) {
    WidgetBackgroundTheme.SYSTEM -> R.string.config_background_color_system
    WidgetBackgroundTheme.LIGHT -> R.string.config_background_color_light
    WidgetBackgroundTheme.DARK -> R.string.config_background_color_dark
}

private fun dateLabelOf(format: ClockDateFormat): Int = when (format) {
    ClockDateFormat.NONE -> R.string.clock_config_date_none
    ClockDateFormat.WEEKDAY -> R.string.clock_config_date_weekday
    ClockDateFormat.LONG -> R.string.clock_config_date_long
    ClockDateFormat.WEEKDAY_AND_LONG -> R.string.clock_config_date_weekday_long
    ClockDateFormat.NUMERIC -> R.string.clock_config_date_numeric
    ClockDateFormat.SHADOK -> R.string.clock_config_date_shadok
}

private fun labelOf(notation: ShadokNotation): Int = when (notation) {
    ShadokNotation.GLYPHS -> R.string.clock_config_notation_glyphs
    ShadokNotation.LABELS -> R.string.clock_config_notation_labels
    ShadokNotation.BASE4 -> R.string.clock_config_notation_base4
}

/** Neuf crans intermédiaires, donc dix intervalles de 10 %. */
private const val OPACITY_STEPS = 9
