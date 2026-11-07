package eu.ttbox.gabuzomeu.tile

import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le calcul du prochain rafraîchissement de la tuile, panneau ouvert. */
class ShadokClockTileServiceTest {

    @Test
    fun `pile sur la minute, on attend la minute entière`() {
        assertEquals(60_050L, millisUntilNextMinute(LocalTime.of(12, 0, 0)))
    }

    @Test
    fun `juste avant la frontière, le délai est très court`() {
        // 12:59:58,500 : il reste 1,5 s, plus la marge.
        assertEquals(1_550L, millisUntilNextMinute(LocalTime.of(12, 59, 58, 500_000_000)))
    }

    @Test
    fun `le réveil tombe toujours après la frontière, jamais avant`() {
        for (second in 0..59) {
            val now = LocalTime.of(23, 59, second, 999_999_999)
            val wake = now.plusNanos(millisUntilNextMinute(now) * 1_000_000)
            assertTrue(wake.second == 0 && wake.minute != now.minute, "réveil à $wake depuis $now")
        }
    }
}
