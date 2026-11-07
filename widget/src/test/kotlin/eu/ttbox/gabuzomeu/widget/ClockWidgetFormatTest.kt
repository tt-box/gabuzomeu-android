package eu.ttbox.gabuzomeu.widget

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Les calculs purs de l'horloge : taille des glyphes et texte de la date. */
internal class ClockWidgetFormatTest {

    private val date = LocalDate.of(2026, 11, 7)

    // ------------------------------------------------------------ taille des glyphes

    @Test
    fun `un widget etroit garde la taille minimale`() {
        assertEquals(14.dp, glyphSizeFor(60.dp))
        assertEquals(14.dp, glyphSizeFor(0.dp))
    }

    @Test
    fun `un widget tres large est plafonne a la taille maximale`() {
        assertEquals(48.dp, glyphSizeFor(2000.dp))
    }

    @Test
    fun `entre les bornes la taille suit la largeur disponible`() {
        // (300 - 2 x 12) / (6 + 0,5) = 42,46 dp.
        assertEquals(276f / 6.5f, glyphSizeFor(300.dp).value, 0.001f)
        assertEquals(true, glyphSizeFor(240.dp) < glyphSizeFor(300.dp))
    }

    // ------------------------------------------------------------------------- date

    @Test
    fun `la date numerique tient sur dix caracteres dans les deux langues`() {
        assertEquals("07/11/2026", plainDateText(ClockDateFormat.NUMERIC, date, Locale.FRANCE))
        assertEquals("07/11/2026", plainDateText(ClockDateFormat.NUMERIC, date, Locale.US))
    }

    @Test
    fun `la date en base 4 se dessine a part`() {
        assertNull(plainDateText(ClockDateFormat.SHADOK, date, Locale.FRANCE))
    }

    @ParameterizedTest
    @EnumSource(value = ClockDateFormat::class, names = ["LONG", "WEEKDAY_AND_LONG"])
    fun `la date lisible suit la langue`(format: ClockDateFormat) {
        assertEquals("7 novembre 2026", plainDateText(format, date, Locale.FRANCE))
        assertEquals("7 November 2026", plainDateText(format, date, Locale.US))
    }

    @Test
    fun `la date lisible sert aussi de description`() {
        assertEquals("7 novembre 2026", readableDate(date, Locale.FRANCE))
    }
}
