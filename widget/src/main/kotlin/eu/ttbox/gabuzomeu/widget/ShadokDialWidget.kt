package eu.ttbox.gabuzomeu.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import java.time.LocalTime
import kotlin.math.min

/**
 * L'horloge Shadok à cadran.
 *
 * Le cadran est peint dans un bitmap ([ShadokDialRenderer]) puis affiché comme image : Glance
 * n'offre aucune surface de dessin. Les quatre chiffres Ga, Bu, Zo et Meu marquent les quarts
 * — les Shadoks comptant en base 4, un cadran en quatre quartiers est leur découpage naturel,
 * et quatre glyphes restent lisibles là où douze nombres se chevaucheraient.
 *
 * **Pas d'aiguille des secondes**, et ce n'est pas un oubli : elle imposerait un réveil par
 * seconde. C'était l'erreur du widget d'origine, dont le `setRepeating` d'une seconde était
 * de toute façon ramené à 60 s et rendu inexact par le système depuis l'API 19.
 *
 * [SizeMode.Exact] : le bitmap est dimensionné sur la place réellement occupée, relue à
 * chaque rendu. Sans cela, un widget redimensionné afficherait un cadran flou ou rogné.
 *
 * La classe est **paramétrée par ses repères** : les deux cadrans proposés — quatre quartiers
 * Ga/Bu/Zo/Meu, ou les douze heures écrites en base 4 — ne diffèrent que par cela. Deux
 * providers distincts, un seul rendu.
 */
internal class ShadokDialWidget(
    private val markers: List<DialMarker>,
    private val markerBoxRatio: Float,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Résolue dynamiquement pour ne pas dépendre de :app, ce qui créerait un cycle.
        val launchComponent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component

        provideContent {
            GlanceTheme {
                val glanceContext = LocalContext.current
                val available = LocalSize.current
                val density = glanceContext.resources.displayMetrics.density
                val options = WidgetBackgroundOptions.from(currentState<Preferences>())
                // L'heure est lue ICI, dans la portée qui lit l'état, et non avant
                // provideContent. Tant que la session Glance est en vie, update() ne rappelle
                // pas provideGlance : il ne fait que réémettre l'état, ce qui recompose cette
                // portée. Lue au-dehors, l'heure restait celle du démarrage de la session, et le
                // cadran manquait le tic de minute comme un changement de fuseau.
                val time = LocalTime.now()
                val palette = widgetPalette(options)

                // Le cadran est un disque : il tient dans le plus petit des deux côtés.
                val sideDp = min(available.width.value, available.height.value) - 2 * PADDING.value
                val sizePx = (sideDp * density).toInt()

                // Le disque du cadran EST l'arriere-plan : il porte donc l'opacite choisie,
                // exactement comme le fond rectangulaire du widget numerique. Les aiguilles et
                // les reperes, eux, restent pleinement opaques — les rendre translucides les
                // rendrait illisibles sur un fond d'ecran charge.
                val dialColors = DialColors(
                    face = palette.background.toArgb(),
                    ring = palette.variant.toArgb(),
                    marker = palette.onSurface.toArgb(),
                    hourHand = palette.onSurface.toArgb(),
                    minuteHand = palette.accent.toArgb(),
                )

                val dial = ShadokDialRenderer.render(
                    context = glanceContext,
                    requestedSizePx = sizePx,
                    hour = time.hour,
                    minute = time.minute,
                    style = DialStyle(
                        colors = dialColors,
                        markers = markers,
                        markerBoxRatio = markerBoxRatio,
                    ),
                )

                // Pas de fond sur le conteneur : le disque peint le sien. Un rectangle opaque
                // derriere un cadran transparent annulerait tout le reglage.
                val base = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .padding(all = PADDING)

                Box(
                    modifier = if (launchComponent == null) {
                        base
                    } else {
                        base.clickable(actionStartActivity(launchComponent))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(dial),
                        // Le cadran est un dessin : ce sont les NOMS qui le décrivent, comme
                        // partout ailleurs dans l'application.
                        contentDescription = null,
                        modifier = GlanceModifier
                            .size(sideDp.dp)
                            .semantics {
                                contentDescription =
                                    ShadokTimeGlyphs.labelsOf(time.hour, time.minute)
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private val PADDING = 8.dp
