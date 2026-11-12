package groma.core

import groma.core.EtapeReleveC0.Avant
import kotlin.test.Test
import kotlin.test.assertEquals

class EtapeReleveC0Test {
    @Test fun `sans centre on releve directement`() {
        assertEquals(Avant.RELEVER, EtapeReleveC0.avant(false, null, false))
        assertEquals(Avant.RELEVER, EtapeReleveC0.avant(false, null, true))
    }

    @Test fun `un centre existant est signale`() {
        assertEquals(Avant.DEJA, EtapeReleveC0.avant(true, "fix", false))
        assertEquals(Avant.DEJA, EtapeReleveC0.avant(true, "telephone", true))
        assertEquals(Avant.DEJA, EtapeReleveC0.avant(true, null, true))
    }

    @Test fun `le telephone ne remplace pas un RTK sans confirmation`() {
        assertEquals(Avant.REMPLACER_RTK, EtapeReleveC0.avant(true, "fix", true))
        assertEquals(Avant.REMPLACER_RTK, EtapeReleveC0.avant(true, "float", true))
    }
}
