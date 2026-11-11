package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EtatEnvoiTerrainTest {
    @Test
    fun rienTantQueRienNEstSaisi() = assertEquals(EtatEnvoiTerrain.Rien, EtatEnvoiTerrain.de(null, null, null))

    @Test
    fun saisieJamaisEnvoyeeEstAEnvoyer() = assertEquals(EtatEnvoiTerrain.AEnvoyer, EtatEnvoiTerrain.de(100, null, null))

    @Test
    fun envoiAJourAfficheLHeure() = assertEquals(EtatEnvoiTerrain.Envoye(5000), EtatEnvoiTerrain.de(100, 100, 5000))

    @Test
    fun saisieFaiteApresLeDebutDeLEnvoiResteAEnvoyer() = assertEquals(EtatEnvoiTerrain.AEnvoyer, EtatEnvoiTerrain.de(150, 100, 5000))

    @Test
    fun envoiSansSaisieConnueResteEnvoye() = assertEquals(EtatEnvoiTerrain.Envoye(7), EtatEnvoiTerrain.de(null, null, 7))
}
