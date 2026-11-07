package eu.ttbox.gabuzomeu.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.ttbox.gabuzomeu.core.shadok.ShadokClock
import eu.ttbox.gabuzomeu.core.shadok.ShadokNotation
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

/**
 * L'horloge Shadok numérique sur l'écran d'accueil.
 *
 * Son affichage se règle **par instance** ([ClockWidgetOptions]) : l'écriture de l'heure —
 * glyphes, noms prononcés ou chiffres bruts en base 4 — la ligne de date, et l'heure décimale
 * en petit. Deux horloges posées côte à côte peuvent donc être réglées différemment.
 *
 * En glyphes, les chiffres sont des ressources vectorielles tintées (voir [ShadokTimeGlyphs]) :
 * Glance rend du `RemoteViews`, où ni un `ImageVector` Compose ni une police embarquée ne sont
 * disponibles. Les noms prononcés restent la description d'accessibilité dans tous les cas —
 * ce sont eux que lit TalkBack, jamais les formes.
 */
class ShadokClockWidget : GlanceAppWidget() {

    // La taille réelle est nécessaire pour dimensionner l'heure.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // L'activité de lanceur est résolue dynamiquement : cela évite au module :widget
        // de dépendre de :app, ce qui créerait un cycle.
        val launchComponent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component

        provideContent {
            // L'état est lu en premier, et l'heure, la date et la locale juste après, dans la
            // MÊME portée de recomposition. C'est ce qui les garde à jour.
            //
            // Elles étaient auparavant lues avant provideContent, en supposant provideGlance
            // rappelé à chaque minute. Ce n'est pas le cas : tant que la session Glance est en
            // vie (une quarantaine de secondes après le dernier rendu), update() et updateAll()
            // ne font que **recomposer** ce bloc. Le tic de minute, un changement de fuseau ou
            // de langue réaffichaient alors les valeurs capturées au démarrage de la session.
            // Glance réémet l'état à chaque update(), ce qui relance cette portée : chaque
            // recomposition relit donc l'horloge.
            val options = ClockWidgetOptions.from(currentState<Preferences>())

            // Par la configuration du Context et non par Locale.getDefault() : cela respecte
            // la langue choisie pour cette application seule (android:localeConfig).
            // L'original figeait les noms de jours et de mois dans des initialiseurs
            // statiques (ClockWidgetProvider.java:22-24), donc au chargement de la classe :
            // un changement de langue ne se voyait qu'après redémarrage du processus.
            val locale = LocalContext.current.resources.configuration.locales[0]
            val time = LocalTime.now()
            val date = LocalDate.now()

            GlanceTheme {
                ClockContent(
                    time = time,
                    date = date,
                    locale = locale,
                    options = options,
                    launchComponent = launchComponent,
                )
            }
        }
    }
}

@Composable
private fun ClockContent(
    time: LocalTime,
    date: LocalDate,
    locale: Locale,
    options: ClockWidgetOptions,
    launchComponent: ComponentName?,
) {
    val available = LocalSize.current
    val palette = widgetPalette(options.background)
    val padding = ClockWidgetLayout.paddingFor(available.height)
    // Ce que la hauteur autorise réellement, et non ce que l'utilisateur a demandé : sur un
    // widget d'une cellule de haut, les lignes de date s'effacent d'elles-mêmes.
    val lines = ClockWidgetLayout.linesFor(options, available.height)
    val (aboveTime, belowTime) = ClockWidgetLayout.splitAroundTime(lines, options.dateAboveTime)

    val base = GlanceModifier
        .fillMaxSize()
        // La couleur porte deja l'opacite choisie : a 0 %, le widget se fond dans le fond
        // d'ecran, sans qu'aucune branche separee ne soit necessaire.
        .background(palette.background)
        .cornerRadius(16.dp)
        .padding(all = padding)

    Column(
        modifier = if (launchComponent == null) {
            base
        } else {
            base.clickable(
                actionStartActivity(launchComponent),
            )
        },
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        aboveTime.forEach { line ->
            ClockSecondaryLine(
                line = line,
                time = time,
                date = date,
                locale = locale,
                options = options,
                palette = palette,
            )
        }

        ShadokTime(
            hour = time.hour,
            minute = time.minute,
            notation = options.notation,
            palette = palette,
        )

        belowTime.forEach { line ->
            ClockSecondaryLine(
                line = line,
                time = time,
                date = date,
                locale = locale,
                options = options,
                palette = palette,
            )
        }
    }
}

/**
 * Une ligne secondaire, au-dessus ou en dessous de l'heure.
 *
 * Extraite pour etre rendue identiquement des deux cotes : dupliquer le `when` aurait laisse les
 * deux copies deriver l'une de l'autre.
 */
@Composable
private fun ClockSecondaryLine(
    line: ClockLine,
    time: LocalTime,
    date: LocalDate,
    locale: Locale,
    options: ClockWidgetOptions,
    palette: WidgetPalette,
) {
    when (line) {
        ClockLine.DECIMAL_TIME -> Text(
            text = String.format(locale, DECIMAL_TIME_FORMAT, time.hour, time.minute),
            style = TextStyle(color = palette.variantProvider, fontSize = 12.sp),
        )

        ClockLine.WEEKDAY -> Text(
            text = date.dayOfWeek
                .getDisplayName(JavaTextStyle.FULL, locale)
                .uppercase(locale),
            style = TextStyle(
                color = palette.accentProvider,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )

        ClockLine.DATE -> ShadokDate(
            date = date,
            locale = locale,
            options = options,
            palette = palette,
        )
    }
}

/**
 * La ligne de date, dans le format choisi.
 *
 * En Shadok, l'année pèse à elle seule six chiffres (2026 = `133222₄`) : onze chiffres au total
 * avec le jour et le mois. Les glyphes de la date sont donc nettement plus petits que ceux de
 * l'heure — sans quoi la ligne déborderait du widget.
 */
@Composable
private fun ShadokDate(
    date: LocalDate,
    locale: Locale,
    options: ClockWidgetOptions,
    palette: WidgetPalette,
) {
    // La date lisible sert aussi de description d'accessibilité : « ◿⅃/… » ne s'énonce pas.
    val readable = DATE_PATTERN.withLocale(locale).format(date)

    when (options.dateFormat) {
        ClockDateFormat.NUMERIC -> DateText(
            text = NUMERIC_DATE_PATTERN.withLocale(locale).format(date),
            spoken = readable,
            palette = palette,
        )

        ClockDateFormat.SHADOK -> ShadokDateLine(
            date = date,
            notation = options.notation,
            spoken = readable,
            palette = palette,
        )

        // LONG et WEEKDAY_AND_LONG. NONE et WEEKDAY ne produisent jamais de ligne de date.
        else -> DateText(text = readable, spoken = readable, palette = palette)
    }
}

@Composable
private fun DateText(text: String, spoken: String, palette: WidgetPalette) {
    Text(
        text = text,
        style = TextStyle(color = palette.variantProvider, fontSize = 13.sp),
        modifier = GlanceModifier.semantics { contentDescription = spoken },
    )
}

/** La date en base 4, dessinée en glyphes ou écrite en noms selon l'écriture de l'heure. */
@Composable
private fun ShadokDateLine(
    date: LocalDate,
    notation: ShadokNotation,
    spoken: String,
    palette: WidgetPalette,
) {
    if (notation != ShadokNotation.GLYPHS) {
        Text(
            text = ShadokTimeGlyphs.formatDate(
                day = date.dayOfMonth,
                month = date.monthValue,
                year = date.year,
                notation = notation,
            ),
            style = TextStyle(color = palette.variantProvider, fontSize = 11.sp),
            modifier = GlanceModifier.semantics { contentDescription = spoken },
        )
        return
    }

    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.semantics { contentDescription = spoken },
    ) {
        ShadokTimeGlyphs.dateSymbolsOf(
            day = date.dayOfMonth,
            month = date.monthValue,
            year = date.year,
        ).forEach { symbol ->
            when (symbol) {
                is ShadokTimeSymbol.Digit -> Image(
                    provider = ImageProvider(ShadokTimeGlyphs.drawableOf(symbol.digit)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(DATE_GLYPH_SIZE),
                    colorFilter = ColorFilter.tint(palette.variantProvider),
                )

                is ShadokTimeSymbol.Separator -> Text(
                    text = symbol.character.toString(),
                    style = TextStyle(
                        color = palette.variantProvider,
                        fontSize = DATE_GLYPH_SIZE.value.sp,
                    ),
                )
            }
        }
    }
}

/**
 * L'heure, dans l'écriture choisie.
 *
 * Les glyphes passent par des images ; les deux autres écritures sont du texte, que
 * [ShadokClock.format] produit déjà. La description d'accessibilité est la même dans les trois
 * cas — les noms prononcés — parce que c'est la seule qui s'énonce.
 */
@Composable
private fun ShadokTime(hour: Int, minute: Int, notation: ShadokNotation, palette: WidgetPalette) {
    val spoken = ShadokTimeGlyphs.labelsOf(hour, minute)
    val size = glyphSizeFor(LocalSize.current.width)

    if (notation == ShadokNotation.GLYPHS) {
        ShadokTimeGlyphRow(
            hour = hour,
            minute = minute,
            glyphSize = size,
            spoken = spoken,
            palette = palette,
        )
        return
    }

    Text(
        text = ShadokClock.format(hour, minute, notation),
        style = TextStyle(
            color = palette.onSurfaceProvider,
            // Les noms sont longs (« BuBuZo:BuBuMeu ») : on les resserre, sans quoi ils
            // déborderaient là où six glyphes tenaient.
            fontSize = (size.value * TEXT_SIZE_RATIO).sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier.semantics { contentDescription = spoken },
    )
}

/**
 * L'heure en glyphes : une suite d'images tintées, séparées par un deux-points en texte.
 *
 * La description d'accessibilité est posée sur la **rangée**, et chaque image est marquée
 * décorative (`contentDescription = null`) : sinon un lecteur d'écran énoncerait sept
 * éléments distincts au lieu d'une heure.
 */
@Composable
private fun ShadokTimeGlyphRow(
    hour: Int,
    minute: Int,
    glyphSize: Dp,
    spoken: String,
    palette: WidgetPalette,
) {
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.semantics { contentDescription = spoken },
    ) {
        ShadokTimeGlyphs.symbolsOf(hour, minute).forEach { symbol ->
            when (symbol) {
                is ShadokTimeSymbol.Digit -> Image(
                    provider = ImageProvider(ShadokTimeGlyphs.drawableOf(symbol.digit)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(glyphSize),
                    colorFilter = ColorFilter.tint(palette.onSurfaceProvider),
                )

                is ShadokTimeSymbol.Separator -> Text(
                    text = symbol.character.toString(),
                    style = TextStyle(
                        color = palette.onSurfaceProvider,
                        fontSize = glyphSize.value.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/**
 * La taille d'un glyphe, déduite de la largeur réellement disponible.
 *
 * Auparavant figée à 22 dp — calculée pour que le pire cas (`23:59`, soit six chiffres) tienne
 * dans les 180 dp de `minWidth`. Un widget agrandi gardait donc des glyphes minuscules perdus
 * au milieu, et la moindre évolution de cette borne de six aurait débordé en silence.
 *
 * Le calcul part du pire cas plutôt que de l'heure courante : sinon l'heure changerait de
 * taille d'une minute à l'autre, ce qui sauterait aux yeux bien plus qu'un glyphe un peu petit.
 */
private fun glyphSizeFor(availableWidth: Dp): Dp {
    val usable = availableWidth.value - 2 * HORIZONTAL_PADDING.value
    val perGlyph = usable / (MAX_GLYPHS_PER_TIME + SEPARATOR_WIDTH_IN_GLYPHS)
    return perGlyph.coerceIn(MIN_GLYPH_SIZE.value, MAX_GLYPH_SIZE.value).dp
}

/** `23:59` = `BuBuMeu:MeuZoMeu` : trois chiffres de chaque côté, et jamais plus. */
private const val MAX_GLYPHS_PER_TIME = 6

/** Le deux-points est bien plus étroit qu'un glyphe : il compte pour environ la moitié. */
private const val SEPARATOR_WIDTH_IN_GLYPHS = 0.5f

/**
 * En noms, l'heure compte jusqu'à seize lettres là où les glyphes n'occupent que six cases :
 * la même hauteur déborderait. Les chiffres base 4 sont plus courts, mais garder une seule
 * règle évite qu'une écriture saute de taille en changeant de réglage.
 */
private const val TEXT_SIZE_RATIO = 0.62f

private const val DECIMAL_TIME_FORMAT = "%02d:%02d"

/**
 * Les glyphes de la date, bien plus petits que ceux de l'heure : onze chiffres à loger contre
 * six, dont six pour la seule année.
 */
private val DATE_GLYPH_SIZE = 11.dp

private val MIN_GLYPH_SIZE = 14.dp
private val MAX_GLYPH_SIZE = 48.dp
private val HORIZONTAL_PADDING = 12.dp

/** Jour, mois en clair, année — comme le widget d'origine, secondes en moins. */
private val DATE_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

/** La forme compacte : lisible même sur un widget étroit. */
private val NUMERIC_DATE_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
