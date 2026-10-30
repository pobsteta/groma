package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import fr.nemeton.terrain.carte.Emprise as TerrainEmprise
import fr.nemeton.terrain.carte.Geoplateforme
import fr.nemeton.terrain.carte.Tuile as TerrainTuile
import fr.nemeton.terrain.carte.ZoneTuiles as TerrainZone

/**
 * Mission 012: terrain-carte v0.7.1 must give exactly what Groma's map code gives today
 * (ZoneTuiles, Emprise, WMTS URLs of Fonds.kt). Written before mission 013 swaps the code:
 * green now (two codes that already exist), and it must stay green afterwards.
 *
 * After 013 `groma.core.ZoneTuiles` is deleted: this file is then adapted by the reviewer
 * (the Groma side becomes the hand-written values below), not silently weakened.
 */
class EquivalenceTerrainCarteTest {
    // Extents (ouest, sud, est, nord) hand-picked from the field cases.
    private val emprises: Map<String, DoubleArray> = linkedMapOf(
        "point" to doubleArrayOf(5.0, 47.0, 5.0, 47.0),
        "foret_1km" to doubleArrayOf(4.98, 47.19, 5.02, 47.21),
        "projet_dijon" to doubleArrayOf(5.0, 47.0, 5.01, 47.01),
        "greenwich" to doubleArrayOf(-0.02, 48.80, 0.03, 48.83),
        "greenwich_pile" to doubleArrayOf(-0.0001, 48.8, 0.0001, 48.8001),
        "equateur_meridien" to doubleArrayOf(-0.01, -0.01, 0.01, 0.01),
        "antimeridien_est" to doubleArrayOf(179.9, 10.0, 180.0, 10.1),
        "antimeridien_ouest" to doubleArrayOf(-180.0, 10.0, -179.9, 10.1),
        "pole_nord" to doubleArrayOf(0.0, 84.9, 1.0, 89.5),
        "pole_sud" to doubleArrayOf(0.0, -89.5, 1.0, -84.9),
        "corse" to doubleArrayOf(8.55, 41.35, 9.55, 43.02),
        "guyane" to doubleArrayOf(-54.6, 2.1, -51.6, 5.8),
        "departement" to doubleArrayOf(4.5, 46.5, 5.9, 47.5),
    )

    private fun g(e: DoubleArray) = Emprise(e[0], e[1], e[2], e[3])
    private fun t(e: DoubleArray) = TerrainEmprise(e[0], e[1], e[2], e[3])

    private fun assertMemeEmprise(a: Emprise, b: TerrainEmprise, msg: String) {
        assertEquals(a.ouest, b.ouest, "$msg ouest")
        assertEquals(a.sud, b.sud, "$msg sud")
        assertEquals(a.est, b.est, "$msg est")
        assertEquals(a.nord, b.nord, "$msg nord")
    }

    @Test
    fun constantes_de_telechargement() {
        assertEquals(12, ZoneTuiles.ZOOM_MIN); assertEquals(12, TerrainZone.ZOOM_MIN)
        assertEquals(18, ZoneTuiles.ZOOM_MAX); assertEquals(18, TerrainZone.ZOOM_MAX)
        assertEquals(200.0, ZoneTuiles.MARGE_M); assertEquals(200.0, TerrainZone.MARGE_M)
        assertEquals(30_000L, ZoneTuiles.NOMBRE_MAX); assertEquals(30_000L, TerrainZone.NOMBRE_MAX)
    }

    @Test
    fun tuile_d_un_point_identique_a_tous_les_zooms() {
        val lons = listOf(-180.0, -179.999, -90.0, -0.0001, 0.0, 0.0001, 2.35, 5.0, 8.55, 90.0, 179.999, 180.0, 200.0, -200.0)
        val lats = listOf(-90.0, -85.06, -85.05112878, -45.0, -0.0001, 0.0, 0.0001, 41.35, 47.0, 48.85, 85.05112878, 85.06, 89.0, 90.0)
        for (z in 0..19) for (lon in lons) for (lat in lats) {
            assertEquals(ZoneTuiles.tuile(lon, lat, z), TerrainZone.tuile(lon, lat, z), "tuile($lon, $lat, z=$z)")
        }
    }

    @Test
    fun tuile_valeurs_ecrites_a_la_main() {
        // Same hand-computed values as ZoneTuilesTest: the reference does not only agree with itself.
        assertEquals(2104 to 1440, TerrainZone.tuile(5.0, 47.0, 12))
        assertEquals(0 to 0, TerrainZone.tuile(-180.0, 89.0, 3))
        assertEquals(7 to 7, TerrainZone.tuile(180.0, -89.0, 3))
        // Greenwich: lon 0 is the first column of the east half (x = n/2), lon -0.0001 the last of the west half.
        assertEquals(2048, TerrainZone.tuile(0.0, 48.8, 12).first)
        assertEquals(2047, TerrainZone.tuile(-0.0001, 48.8, 12).first)
        // Equator: lat 0 is the first row of the south half (y = n/2).
        assertEquals(2048, TerrainZone.tuile(0.0, 0.0, 12).second)
    }

    @Test
    fun plages_et_nombre_identiques_sur_toutes_les_emprises_et_zooms() {
        for ((nom, e) in emprises) {
            for (zmin in 0..19) for (zmax in zmin..19) {
                val a = ZoneTuiles(g(e), zmin, zmax)
                val b = TerrainZone(t(e), zmin, zmax)
                for (z in zmin..zmax) assertEquals(a.plage(z), b.plage(z), "$nom plage z=$z")
                assertEquals(a.nombre, b.nombre, "$nom nombre $zmin..$zmax")
            }
            val a = ZoneTuiles(g(e)); val b = TerrainZone(t(e))
            assertEquals(a.zoomMin, b.zoomMin, "$nom zoomMin par défaut")
            assertEquals(a.zoomMax, b.zoomMax, "$nom zoomMax par défaut")
            assertEquals(a.nombre, b.nombre, "$nom nombre par défaut")
        }
    }

    @Test
    fun bornes_12_et_18_une_seule_tuile_par_zoom_sur_un_point() {
        val p = TerrainEmprise(5.0, 47.0, 5.0, 47.0)
        val z12 = TerrainZone(p, 12, 12)
        assertEquals(1L, z12.nombre)
        assertEquals(listOf(TerrainTuile(12, 2104, 1440)), z12.tuiles().toList())
        assertEquals(1L, TerrainZone(p, 18, 18).nombre)
        assertEquals(1L, ZoneTuiles(Emprise(5.0, 47.0, 5.0, 47.0), 18, 18).nombre)
        // Default zooms 12..18: seven zooms, one tile each.
        assertEquals(7L, TerrainZone(p).nombre)
        assertEquals(7L, ZoneTuiles(Emprise(5.0, 47.0, 5.0, 47.0)).nombre)
    }

    @Test
    fun plage_valeurs_ecrites_a_la_main_foret_1km() {
        val b = TerrainZone(TerrainEmprise(4.98, 47.19, 5.02, 47.21), 17, 18)
        // Hand values of ZoneTuilesTest: zoom 18, columns 134698..134727 (30), rows 91978..91999 (22).
        assertEquals((134698..134727) to (91978..91999), b.plage(18))
        val (xs17, ys17) = b.plage(17)
        assertEquals(30L * 22 + xs17.count().toLong() * ys17.count(), b.nombre)
        assertEquals(TerrainTuile(18, 134727, 91999), b.tuiles().last())
        assertEquals(17, b.tuiles().first().z)
    }

    @Test
    fun greenwich_plage_a_cheval_sur_le_meridien() {
        val e = emprises.getValue("greenwich")
        for (z in 12..18) {
            val n = 1 shl z
            val (xs, _) = TerrainZone(t(e)).plage(z)
            assertTrue(xs.first <= n / 2 - 1, "z=$z: l'ouest reste dans la moitié ouest")
            assertEquals(TerrainZone.tuile(-0.02, 48.80, z).first, xs.first, "z=$z: première colonne = tuile de lon -0.02")
            assertTrue(xs.last >= n / 2, "z=$z: l'est passe la colonne n/2")
            assertEquals(ZoneTuiles(g(e)).plage(z), TerrainZone(t(e)).plage(z), "z=$z")
        }
    }

    @Test
    fun ordre_et_contenu_des_tuiles_identiques() {
        for (nom in listOf("point", "foret_1km", "greenwich_pile", "equateur_meridien", "antimeridien_est", "pole_nord")) {
            val e = emprises.getValue(nom)
            for ((zmin, zmax) in listOf(12 to 14, 17 to 18, 12 to 12, 18 to 18)) {
                val a = ZoneTuiles(g(e), zmin, zmax).tuiles().map { Triple(it.z, it.x, it.y) }.toList()
                val b = TerrainZone(t(e), zmin, zmax).tuiles().map { Triple(it.z, it.x, it.y) }.toList()
                assertEquals(a, b, "$nom $zmin..$zmax")
                assertEquals(TerrainZone(t(e), zmin, zmax).nombre, b.size.toLong(), "$nom $zmin..$zmax nombre = taille")
            }
        }
    }

    @Test
    fun marge_du_projet_identique() {
        val marges = listOf(null, 0.0, 50.0, 200.0, 500.0, 1000.0)
        for ((nom, e) in emprises) for (m in marges) {
            val a = if (m == null) ZoneTuiles.projet(g(e)) else ZoneTuiles.projet(g(e), m)
            val b = if (m == null) TerrainZone.projet(t(e)) else TerrainZone.projet(t(e), m)
            assertMemeEmprise(a.emprise, b.emprise, "$nom marge=$m")
            assertEquals(a.zoomMin, b.zoomMin); assertEquals(a.zoomMax, b.zoomMax)
            assertEquals(a.nombre, b.nombre, "$nom marge=$m nombre")
            for (z in 12..18) assertEquals(a.plage(z), b.plage(z), "$nom marge=$m z=$z")
        }
    }

    @Test
    fun marge_valeurs_ecrites_a_la_main() {
        val z = TerrainZone.projet(TerrainEmprise(5.0, 47.0, 5.01, 47.01))
        // 200 m / 111320 = 0.0018° of latitude; 200 / (111320 cos 47.005°) = 0.0026° of longitude.
        assertEquals(47.0 - 0.0018, z.emprise.sud, 1e-4)
        assertEquals(47.01 + 0.0018, z.emprise.nord, 1e-4)
        assertEquals(5.0 - 0.0026, z.emprise.ouest, 1e-4)
        assertEquals(5.01 + 0.0026, z.emprise.est, 1e-4)
        assertEquals(12, z.zoomMin); assertEquals(18, z.zoomMax)
    }

    @Test
    fun limite_de_30000_tuiles_meme_decision() {
        var petit = 0; var refuse = 0
        for ((nom, e) in emprises) {
            val a = ZoneTuiles.projet(g(e)).nombre
            val b = TerrainZone.projet(t(e)).nombre
            assertEquals(a, b, nom)
            assertEquals(a > ZoneTuiles.NOMBRE_MAX, b > TerrainZone.NOMBRE_MAX, "$nom décision de refus")
            if (b > TerrainZone.NOMBRE_MAX) refuse++ else petit++
        }
        // The set must exercise both sides of the limit, or this test proves nothing.
        assertTrue(refuse > 0 && petit > 0, "refuse=$refuse petit=$petit")
        assertTrue(TerrainZone.projet(t(emprises.getValue("departement"))).nombre > TerrainZone.NOMBRE_MAX)
        assertTrue(TerrainZone.projet(t(emprises.getValue("foret_1km"))).nombre <= TerrainZone.NOMBRE_MAX)
    }

    @Test
    fun limite_au_cordeau_autour_de_30000() {
        // Grow a square extent around Dijon until the count crosses the limit: both codes give the
        // same count and the same verdict at every step.
        var franchi = false
        var demi = 0.005
        while (demi < 1.0) {
            val a = ZoneTuiles(Emprise(5.0 - demi, 47.0 - demi, 5.0 + demi, 47.0 + demi)).nombre
            val b = TerrainZone(TerrainEmprise(5.0 - demi, 47.0 - demi, 5.0 + demi, 47.0 + demi)).nombre
            assertEquals(a, b, "demi-côté=$demi")
            assertEquals(a > ZoneTuiles.NOMBRE_MAX, b > TerrainZone.NOMBRE_MAX, "demi-côté=$demi")
            if (b > TerrainZone.NOMBRE_MAX) franchi = true
            demi += 0.005
        }
        assertTrue(franchi, "the sweep never crossed the 30 000 limit")
    }

    @Test
    fun zooms_invalides_refuses_pareillement() {
        for ((zmin, zmax) in listOf(-1 to 5, 13 to 12, 19 to 18, -3 to -2)) {
            val ma = assertFailsWith<IllegalArgumentException>("groma $zmin..$zmax") { ZoneTuiles(Emprise(5.0, 47.0, 5.1, 47.1), zmin, zmax) }
            val mb = assertFailsWith<IllegalArgumentException>("terrain $zmin..$zmax") { TerrainZone(TerrainEmprise(5.0, 47.0, 5.1, 47.1), zmin, zmax) }
            assertEquals("Zooms invalides.", ma.message)
            assertEquals(ma.message, mb.message)
        }
    }

    @Test
    fun emprise_inversee_meme_resultat() {
        // Incoherent extent (west > east, south > north): same outcome in both, whatever it is.
        val e = doubleArrayOf(5.1, 47.1, 5.0, 47.0)
        assertEquals(ZoneTuiles(g(e)).nombre, TerrainZone(t(e)).nombre)
        assertEquals(ZoneTuiles(g(e)).plage(15), TerrainZone(t(e)).plage(15))
    }

    // --- WMTS URLs ---------------------------------------------------------------------------

    /**
     * Copy of `SourceGeoplateforme.getTileURLString` (app/carte/Fonds.kt) with baseUrl
     * "https://data.geopf.fr/wmts". The class is private to the Android module and extends an
     * osmdroid class, so groma-core cannot call it: this copy is the reference and the literal
     * URLs further down are the hand-written check of the copy.
     */
    private fun urlGroma(couche: String, style: String, format: String, z: Int, x: Int, y: Int): String =
        "https://data.geopf.fr/wmts" + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=" + couche +
            "&STYLE=" + style.replace(" ", "%20") + "&FORMAT=" + format + "&TILEMATRIXSET=PM_0_19" +
            "&TILEMATRIX=" + z + "&TILEROW=" + y + "&TILECOL=" + x

    private val tuilesUrl = listOf(
        Triple(0, 0, 0), Triple(12, 2104, 1440), Triple(15, 16841, 11520), Triple(18, 134698, 91978),
        Triple(19, 269396, 183956), Triple(18, 0, 262143),
    )

    @Test
    fun url_plan_ign_octet_pour_octet() {
        for ((z, x, y) in tuilesUrl) {
            assertEquals(urlGroma("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "normal", "image/png", z, x, y), Geoplateforme.PLAN_IGN.url(z, x, y))
        }
    }

    @Test
    fun url_ortho_ign_octet_pour_octet() {
        for ((z, x, y) in tuilesUrl) {
            assertEquals(urlGroma("ORTHOIMAGERY.ORTHOPHOTOS", "normal", "image/jpeg", z, x, y), Geoplateforme.ORTHO_IGN.url(z, x, y))
        }
    }

    @Test
    fun url_cadastre_octet_pour_octet_avec_espace_encode() {
        for ((z, x, y) in tuilesUrl) {
            assertEquals(urlGroma("CADASTRALPARCELS.PARCELLAIRE_EXPRESS", "PCI vecteur", "image/png", z, x, y), Geoplateforme.CADASTRE.url(z, x, y))
        }
        assertTrue("STYLE=PCI%20vecteur&" in Geoplateforme.CADASTRE.url(15, 1, 2))
    }

    @Test
    fun url_valeurs_ecrites_a_la_main_et_ordre_ligne_colonne() {
        // TILEROW is y, TILECOL is x (a swap would put the tile in the wrong place).
        assertEquals(
            "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                "&LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2&STYLE=normal&FORMAT=image/png" +
                "&TILEMATRIXSET=PM_0_19&TILEMATRIX=15&TILEROW=11520&TILECOL=16841",
            Geoplateforme.PLAN_IGN.url(15, 16841, 11520),
        )
        assertEquals(
            "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&FORMAT=image/jpeg" +
                "&TILEMATRIXSET=PM_0_19&TILEMATRIX=12&TILEROW=1440&TILECOL=2104",
            Geoplateforme.ORTHO_IGN.url(12, 2104, 1440),
        )
        assertEquals(
            "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                "&LAYER=CADASTRALPARCELS.PARCELLAIRE_EXPRESS&STYLE=PCI%20vecteur&FORMAT=image/png" +
                "&TILEMATRIXSET=PM_0_19&TILEMATRIX=18&TILEROW=91978&TILECOL=134698",
            Geoplateforme.CADASTRE.url(18, 134698, 91978),
        )
    }

    @Test
    fun noms_extensions_et_zooms_des_sources() {
        // Names are the folder names of downloaded tiles (sourceParNom): changing one orphans offline tiles.
        assertEquals(listOf("PlanIGN", "OrthoIGN", "CadastreIGN"), Geoplateforme.TOUTES.map { it.nom })
        assertEquals(".png", Geoplateforme.PLAN_IGN.extension)
        assertEquals(".jpg", Geoplateforme.ORTHO_IGN.extension)
        assertEquals(".png", Geoplateforme.CADASTRE.extension)
        for (c in Geoplateforme.TOUTES) {
            assertEquals(0, c.zoomMin, c.nom); assertEquals(19, c.zoomMax, c.nom)
        }
        assertEquals(Geoplateforme.CADASTRE, Geoplateforme.parNom("CadastreIGN"))
        assertNull(Geoplateforme.parNom("OSM"))
    }
}
