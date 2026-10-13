package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CleProjetTest {
    private fun infos(id: String) = InfosProjet(id, "Forêt", null, null, null, null, null, null, null, null, null, null, null, null, null)

    @Test
    fun projetIdDeLaTablePuisDuDossier() {
        assertEquals("20261008_212542_uywc", CleProjet.projetId(infos("20261008_212542_uywc"), "autre"))
        // No table (GeoPackage prepared before schema 1.1.0): folder name, made safe.
        assertEquals("plan_20261008", CleProjet.projetId(null, "plan_20261008"))
        assertEquals("For_t_de_Couchey__ONF_", CleProjet.projetId(null, "Forêt de Couchey (ONF)"))
        assertEquals("a_b", CleProjet.projetId(infos("a/b"), "a b"))
        assertEquals("projet", CleProjet.projetId(null, ".."))
    }

    @Test
    fun memePlacetteDeuxProjets() {
        assertEquals("foret_a/P001.groma.zip", CleProjet.chemin("foret_a", "P001", ".groma.zip"))
        assertNotEquals(CleProjet.chemin("foret_a", "P001"), CleProjet.chemin("foret_b", "P001"))
        // No ambiguity between ("a_b", "c") and ("a", "b_c").
        assertNotEquals(CleProjet.travailEnvoi("a_b", "c"), CleProjet.travailEnvoi("a", "b_c"))
        assertFailsWith<IllegalArgumentException> { CleProjet.chemin("../x", "P001") }
        assertFailsWith<IllegalArgumentException> { CleProjet.chemin("foret_a", "") }
    }
}
