package groma.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtatCentreTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test fun `non releve`() = assertEquals("Centre C0 non relevé", EtatCentre.ligne(false, null, null, null, paris))

    @Test fun `rtk fixe avec precision et date locale`() = assertEquals(
        "Centre C0 : RTK fixe, précision 0,02 m, relevé le 12/11 à 10:05",
        EtatCentre.ligne(true, "fix", 0.02, "2026-11-12T09:05:00.123Z", paris),
    )

    @Test fun `flottant et telephone en clair`() {
        assertEquals("Centre C0 : RTK flottant, précision 0,35 m, relevé le 01/07 à 14:30", EtatCentre.ligne(true, "float", 0.35, "2026-07-01T12:30:00Z", paris))
        assertEquals("Centre C0 : GNSS du téléphone, précision 3,10 m, relevé le 12/11 à 10:05", EtatCentre.ligne(true, "telephone", 3.1, "2026-11-12T09:05:00Z", paris))
    }

    @Test fun `date absente ou illisible omise sans planter`() {
        assertEquals("Centre C0 : RTK fixe, précision 0,02 m", EtatCentre.ligne(true, "fix", 0.02, null, paris))
        assertNull(EtatCentre.dateLocale("n'importe quoi", paris))
    }

    @Test fun `formats de date d'autres outils`() {
        assertEquals("12/11 à 10:05", EtatCentre.dateLocale("2026-11-12T11:05:00+02:00", paris))
        assertEquals("12/11 à 10:05", EtatCentre.dateLocale("2026-11-12 09:05:00", paris))
    }

    @Test fun `seuil de 30 m non bloquant a la limite`() {
        assertFalse(EtatCentre.eloigne(null))
        assertFalse(EtatCentre.eloigne(30.0))
        assertTrue(EtatCentre.eloigne(30.1))
        assertFalse(EtatCentre.eloigne(4.0))
    }
}
