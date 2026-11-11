package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mission 017: copy rule of the QField plugin (`qfield/inventaire_video.qml`), written as a pure
 * function that the QML reproduces line for line (Kotlin is the reference, like the numbering).
 *
 * API EXPECTED FROM MISSION 018:
 *
 * ```
 * package groma.core
 * object RecopieQfield {
 *     /** Attributes the plugin may copy: essence_arbre, dbh_cm, dbh_source, statut, qualite. */
 *     val ATTRIBUTS: List<String>
 *
 *     /**
 *      * @param groma  one tree as sent by the local server (keys among ATTRIBUTS; missing or null = empty)
 *      * @param qfield the same tree as it is in QField's copy of `arbres`
 *      * @return ONLY the attributes to write in QField (empty map = nothing to do). Never a key
 *      *         whose QField value is a real entry.
 *      */
 *     fun aRecopier(groma: Map<String, Any?>, qfield: Map<String, Any?>): Map<String, Any?>
 * }
 * ```
 *
 * Rules (decision of Pascal, 2026-11-11):
 *  - essence_arbre, qualite: copied only if the QField value is empty (null or blank) and the Groma one is not;
 *  - statut: copied only if the QField value is empty or "vivant" (the default) and the Groma one is
 *    neither empty nor "vivant";
 *  - dbh_cm + dbh_source go together: copied only if Groma has dbh_source = "mesure" and QField's
 *    dbh_source is empty or "estime". (QField's dbh_cm is never empty: it holds the video estimate,
 *    hence this rule instead of "empty"; a tape diameter typed in QField, dbh_source = "mesure", is never overwritten.)
 */
class RecopieQfieldTest {
    private fun r(groma: Map<String, Any?>, qfield: Map<String, Any?>) = RecopieQfield.aRecopier(groma, qfield)

    @Test
    fun attributs() {
        assertEquals(setOf("essence_arbre", "dbh_cm", "dbh_source", "statut", "qualite"), RecopieQfield.ATTRIBUTS.toSet())
    }

    @Test
    fun essenceEtQualiteRecopieesSiVidesDansQfield() {
        val g = mapOf("essence_arbre" to "hetre", "qualite" to "B")
        assertEquals(g, r(g, mapOf("essence_arbre" to null, "qualite" to null)))
        assertEquals(g, r(g, mapOf("essence_arbre" to "", "qualite" to "  ")))
        assertEquals(g, r(g, emptyMap()))
    }

    @Test
    fun jamaisDEcrasementDUneSaisieQfield() {
        val g = mapOf("essence_arbre" to "hetre", "qualite" to "B", "statut" to "mort")
        val q = mapOf("essence_arbre" to "charme", "qualite" to "A", "statut" to "chablis")
        assertTrue(r(g, q).isEmpty())
        // One attribute typed in QField does not block the others.
        assertEquals(mapOf("qualite" to "B"), r(mapOf("essence_arbre" to "hetre", "qualite" to "B"), mapOf("essence_arbre" to "charme")))
        // Same value on both sides: nothing to write.
        assertTrue(r(mapOf("essence_arbre" to "hetre"), mapOf("essence_arbre" to "hetre")).isEmpty())
    }

    @Test
    fun valeurVideDeGromaNeRecopieRien() {
        assertTrue(r(mapOf("essence_arbre" to null, "qualite" to "", "statut" to null), mapOf("essence_arbre" to "charme", "qualite" to null)).isEmpty())
        assertTrue(r(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun statut() {
        assertEquals(mapOf("statut" to "mort"), r(mapOf("statut" to "mort"), mapOf("statut" to "vivant")))
        assertEquals(mapOf("statut" to "mort"), r(mapOf("statut" to "mort"), mapOf("statut" to null)))
        assertEquals(mapOf("statut" to "coupe"), r(mapOf("statut" to "coupe"), mapOf("statut" to "")))
        assertTrue(r(mapOf("statut" to "mort"), mapOf("statut" to "chablis")).isEmpty())
        assertTrue(r(mapOf("statut" to "vivant"), mapOf("statut" to null)).isEmpty(), "vivant est la valeur par défaut : rien à recopier")
        assertTrue(r(mapOf("statut" to "vivant"), mapOf("statut" to "mort")).isEmpty(), "jamais d'écrasement, même par vivant")
    }

    @Test
    fun diametreMesureRecopieAvecSaSource() {
        val g = mapOf("dbh_cm" to 47.5, "dbh_source" to "mesure")
        assertEquals(g, r(g, mapOf("dbh_cm" to 40.0, "dbh_source" to "estime")))
        assertEquals(g, r(g, mapOf("dbh_cm" to 40.0, "dbh_source" to null)))
        assertEquals(g, r(g, mapOf("dbh_cm" to 40.0)))
    }

    @Test
    fun diametreEstimeOuDejaMesureDansQfieldNEstPasTouche() {
        // Groma side only an estimate: the QField estimate is not touched.
        assertTrue(r(mapOf("dbh_cm" to 47.5, "dbh_source" to "estime"), mapOf("dbh_cm" to 40.0, "dbh_source" to "estime")).isEmpty())
        // Tape diameter already typed in QField: kept.
        assertTrue(r(mapOf("dbh_cm" to 47.5, "dbh_source" to "mesure"), mapOf("dbh_cm" to 44.0, "dbh_source" to "mesure")).isEmpty())
    }

    @Test
    fun arbreCompletDansLeCasNominal() {
        val g = mapOf("essence_arbre" to "hetre", "dbh_cm" to 47.5, "dbh_source" to "mesure", "statut" to "mort", "qualite" to "B")
        val q = mapOf("essence_arbre" to null, "dbh_cm" to 40.0, "dbh_source" to "estime", "statut" to "vivant", "qualite" to null, "notes" to "fourche")
        assertEquals(g, r(g, q))
    }

    @Test
    fun neRetourneQueLesAttributsDeLaListe() {
        val res = r(mapOf("essence_arbre" to "hetre", "notes" to "x", "photos" to "p.jpg", "h_m" to 12.0), emptyMap())
        assertEquals(setOf("essence_arbre"), res.keys)
    }
}
