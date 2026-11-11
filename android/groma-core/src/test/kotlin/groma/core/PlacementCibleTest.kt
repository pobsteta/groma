package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Mission 015 : tests écrits avant le code (mission 016) de la vue « Cibles ».
 *
 * API attendue de `groma.core.PlacementCible` (nom et signatures à confirmer avec 016 ;
 * si 016 les change, seul ce bloc est à adapter, pas les valeurs attendues) :
 *
 * ```
 * enum class SourceAzimut(val code: String) { BOUSSOLE_VISEE("boussole_visee"), BOUSSOLE_TELEPHONE("boussole_telephone") }
 * enum class TypeAlerte { ECART_ANGULAIRE, TROP_PROCHE_DU_CENTRE, MOINS_DE_TROIS_CIBLES }
 * data class AlerteCible(val type: TypeAlerte, val cibles: List<String>)   // cibles triées, vide si globale
 * data class CiblePlacee(val id: String, val azimutDeg: Double /* magnétique, degrés */,
 *                        val distanceM: Double, val dbhCm: Double, val source: SourceAzimut)
 *
 * object PlacementCible {
 *   const val DISTANCE_MAX_M = 60.0; const val DIAMETRE_MAX_CM = 1000.0
 *   const val ECART_ANGULAIRE_MIN_DEG = 60.0      // valeur provisoire (fiche 015), gel après 3 placettes test
 *   const val DISTANCE_MIN_CENTRE_M = 8.0         // valeur provisoire (fiche 015)
 *   // Lambert 93 (x, y). Lève IllegalArgumentException si la saisie est refusée.
 *   fun position(centre: Pair<Double, Double>, azimut: Double, unite: String,
 *                declinaisonDeg: Double, convergenceDeg: Double, distanceM: Double): Pair<Double, Double>
 *   // Contrôle de saisie ; lève IllegalArgumentException (refus). azimut dans l'unité de la placette.
 *   fun controler(id: String, azimut: Double, unite: String, distanceM: Double, dbhCm: Double)
 *   // Azimut du capteur (toujours en degrés) -> unité de la placette, pour l'affichage.
 *   fun azimutAffiche(azimutTelephoneDeg: Double, unite: String): Double
 *   // Remplace la cible de même id (l'ancienne n'est pas gardée) ou ajoute ; ordre C1, C2, C3.
 *   fun placer(existantes: List<CiblePlacee>, nouvelle: CiblePlacee): List<CiblePlacee>
 *   // Alertes de répartition ; jamais un refus.
 *   fun alertes(cibles: List<CiblePlacee>): List<AlerteCible>
 * }
 * ```
 *
 * Valeurs attendues de position : recopiées à la main de `azimut_grille_mesure`
 * (core/src/groma/georeferencement.py) puis x = cx + d sin(az), y = cy + d cos(az) ; jamais de l'implémentation.
 * Convergence Lambert 93 = 0,7256077650532670 (lon - 3) : Brest (-4,49) -> -5,434802 ; Strasbourg (7,75) -> 3,446637.
 */
class PlacementCibleTest {
    private val centre = Pair(925000.0, 6700000.0)
    private val tolMm = 0.001

    private fun pos(az: Double, u: String, decl: Double, conv: Double, d: Double, dx: Double, dy: Double) {
        val (x, y) = PlacementCible.position(centre, az, u, decl, conv, d)
        assertEquals(centre.first + dx, x, tolMm, "x pour $az $u")
        assertEquals(centre.second + dy, y, tolMm, "y pour $az $u")
    }

    // ---- 1. position ----

    @Test fun nord() = pos(0.0, "deg", 0.0, 0.0, 10.0, 0.0, 10.0)
    @Test fun est() = pos(90.0, "deg", 0.0, 0.0, 10.0, 10.0, 0.0)
    @Test fun sud() = pos(180.0, "deg", 0.0, 0.0, 10.0, 0.0, -10.0)
    @Test fun ouest() = pos(270.0, "deg", 0.0, 0.0, 10.0, -10.0, 0.0)

    @Test fun zeroGradeEstLeNord() = pos(0.0, "gr", 0.0, 0.0, 10.0, 0.0, 10.0)
    @Test fun centGradesEstLEst() = pos(100.0, "gr", 0.0, 0.0, 10.0, 10.0, 0.0)

    @Test fun grade399virgule9() =
        // Python : grille 359,91 deg ; dx -0.031415913616638286, dy 19.999975325994072
        pos(399.9, "gr", 0.0, 0.0, 20.0, -0.031415913616638286, 19.999975325994072)

    @Test fun degres359virgule5() =
        pos(359.5, "deg", 0.0, 0.0, 10.0, -0.08726535498373636, 9.999619230641713)

    @Test fun declinaisonNegative() =
        // 45 deg, decl -2,5 -> 42,5 deg ; Python dx 8.444877595195752, dy 9.21596671012655
        pos(45.0, "deg", -2.5, 0.0, 12.5, 8.444877595195752, 9.21596671012655)

    @Test fun convergenceDeBrest() =
        // 100 gr, decl -1,2, conv -5,434802 -> grille 94,23480216 deg
        pos(100.0, "gr", -1.2, -5.43480216024897, 15.0, 14.959047112008578, -1.1076594696511104)

    @Test fun convergenceDeStrasbourg() =
        // 250 gr, decl +1,9, conv +3,446637 -> grille 223,45336312 deg
        pos(250.0, "gr", 1.9, 3.446636884003018, 15.0, -10.316458751948531, -10.889016430300055)

    @Test fun convergencesDeReferenceCoherentesAvecAzimut() {
        assertEquals(-5.43480216024897, Azimut.convergenceLambert93(-4.49), 1e-9)
        assertEquals(3.446636884003018, Azimut.convergenceLambert93(7.75), 1e-9)
    }

    @Test fun unDegreDErreurDecale26cmAQuinzeMetres() {
        val (x0, y0) = PlacementCible.position(centre, 90.0, "deg", 0.0, 0.0, 15.0)
        val (x1, y1) = PlacementCible.position(centre, 91.0, "deg", 0.0, 0.0, 15.0)
        val ecart = Math.hypot(x1 - x0, y1 - y0)
        assertEquals(0.2618, ecart, 0.0005) // 2 * 15 * sin(0,5 deg)
    }

    // ---- 2. saisie refusée ----

    private fun refus(id: String = "C1", az: Double = 100.0, u: String = "deg", d: Double = 10.0, dbh: Double = 30.0) {
        assertFailsWith<IllegalArgumentException>("devait refuser $id $az $u $d $dbh") {
            PlacementCible.controler(id, az, u, d, dbh)
        }
    }

    private fun accepte(id: String = "C1", az: Double = 100.0, u: String = "deg", d: Double = 10.0, dbh: Double = 30.0) =
        PlacementCible.controler(id, az, u, d, dbh)

    @Test fun saisieNominaleAcceptee() = accepte()

    @Test fun distanceNulleAcceptee() = accepte(d = 0.0) // décision de Pascal du 2026-11-11 (schéma : min 0)
    @Test fun distanceNegativeRefusee() = refus(d = -1.0)
    @Test fun distanceAuDessusDe60Refusee() = refus(d = 60.01)
    @Test fun distanceExactement60Acceptee() = accepte(d = 60.0)
    @Test fun distanceMinusculeAcceptee() = accepte(d = 0.01)
    @Test fun distanceNonNumeriqueRefusee() { refus(d = Double.NaN); refus(d = Double.POSITIVE_INFINITY) }

    @Test fun azimutNegatifRefuseEnDegres() = refus(az = -0.1, u = "deg")
    @Test fun azimut360RefuseEnDegres() = refus(az = 360.0, u = "deg")
    @Test fun azimut359virgule99AccepteEnDegres() = accepte(az = 359.99, u = "deg")
    @Test fun azimutZeroAccepteEnDegres() = accepte(az = 0.0, u = "deg")
    @Test fun azimut360AccepteEnGrades() = accepte(az = 360.0, u = "gr") // hors [0,360[ mais dans [0,400[
    @Test fun azimut400RefuseEnGrades() = refus(az = 400.0, u = "gr")
    @Test fun azimutNegatifRefuseEnGrades() = refus(az = -0.1, u = "gr")
    @Test fun azimut399virgule9AccepteEnGrades() = accepte(az = 399.9, u = "gr")
    @Test fun azimutNaNRefuse() = refus(az = Double.NaN)
    @Test fun uniteInconnueRefusee() = refus(u = "rad")

    @Test fun diametreNulAccepte() = accepte(dbh = 0.0) // décision de Pascal du 2026-11-11 (schéma : min 0)
    @Test fun diametreNegatifRefuse() = refus(dbh = -5.0)
    @Test fun diametreAuDessusDe1000Refuse() = refus(dbh = 1000.1)
    @Test fun diametreExactement1000Accepte() = accepte(dbh = 1000.0)
    @Test fun diametreNaNRefuse() = refus(dbh = Double.NaN)

    @Test fun identifiantsAutorisesC1C2C3() { accepte(id = "C1"); accepte(id = "C2"); accepte(id = "C3") }
    @Test fun identifiantC0Refuse() = refus(id = "C0") // C0 = la canne, pas une cible saisie
    @Test fun identifiantC4Refuse() = refus(id = "C4")
    @Test fun identifiantMinusculeRefuse() = refus(id = "c1")
    @Test fun identifiantVideRefuse() = refus(id = "")

    @Test fun positionRefuseLesMemesSaisies() {
        assertFailsWith<IllegalArgumentException> { PlacementCible.position(centre, 100.0, "deg", 0.0, 0.0, 60.01) }
        assertFailsWith<IllegalArgumentException> { PlacementCible.position(centre, 400.0, "gr", 0.0, 0.0, 10.0) }
        assertFailsWith<IllegalArgumentException> { PlacementCible.position(centre, 360.0, "deg", 0.0, 0.0, 10.0) }
    }

    // ---- 3. source de l'azimut ----

    @Test fun codesDeSource() {
        assertEquals("boussole_visee", SourceAzimut.BOUSSOLE_VISEE.code)
        assertEquals("boussole_telephone", SourceAzimut.BOUSSOLE_TELEPHONE.code)
        assertEquals(2, SourceAzimut.values().size)
    }

    @Test fun capteurEnDegresAfficheEnDegres() =
        assertEquals(123.4, PlacementCible.azimutAffiche(123.4, "deg"), 1e-9)

    @Test fun capteurEnDegresAfficheEnGrades() {
        assertEquals(100.0, PlacementCible.azimutAffiche(90.0, "gr"), 1e-9)
        assertEquals(200.0, PlacementCible.azimutAffiche(180.0, "gr"), 1e-9)
        assertEquals(399.9, PlacementCible.azimutAffiche(359.91, "gr"), 1e-9)
    }

    @Test fun capteurHorsPlageRamenePuisAffiche() {
        // le capteur peut rendre 360 ou une valeur négative : l'affichage reste dans [0, 400[
        assertEquals(0.0, PlacementCible.azimutAffiche(360.0, "gr"), 1e-9)
        assertEquals(399.9, PlacementCible.azimutAffiche(-0.09, "gr"), 1e-9)
    }

    @Test fun allerRetourCapteurGradesPuisPosition() {
        // la valeur affichée (grades) donne la même position que le capteur en degrés
        val affiche = PlacementCible.azimutAffiche(37.0, "gr")
        val a = PlacementCible.position(centre, affiche, "gr", 1.0, 2.0, 10.0)
        val b = PlacementCible.position(centre, 37.0, "deg", 1.0, 2.0, 10.0)
        assertEquals(b.first, a.first, tolMm); assertEquals(b.second, a.second, tolMm)
    }

    // ---- 4. répartition (alertes, jamais un refus) ----

    private fun c(id: String, az: Double, d: Double = 10.0, src: SourceAzimut = SourceAzimut.BOUSSOLE_VISEE) =
        CiblePlacee(id, az, d, 30.0, src)

    private fun types(l: List<CiblePlacee>) = PlacementCible.alertes(l).map { it.type }

    @Test fun troisCiblesBienReparties_aucuneAlerte() =
        assertEquals(emptyList(), PlacementCible.alertes(listOf(c("C1", 0.0), c("C2", 120.0), c("C3", 240.0))))

    @Test fun valeursProvisoires() {
        assertEquals(60.0, PlacementCible.ECART_ANGULAIRE_MIN_DEG, 0.0)
        assertEquals(8.0, PlacementCible.DISTANCE_MIN_CENTRE_M, 0.0)
        assertEquals(60.0, PlacementCible.DISTANCE_MAX_M, 0.0)
        assertEquals(1000.0, PlacementCible.DIAMETRE_MAX_CM, 0.0)
    }

    @Test fun deuxCiblesTropProchesEnAzimut() {
        val a = PlacementCible.alertes(listOf(c("C1", 0.0), c("C2", 59.9), c("C3", 200.0)))
        assertEquals(listOf(AlerteCible(TypeAlerte.ECART_ANGULAIRE, listOf("C1", "C2"))), a)
    }

    @Test fun ecartExactement60_pasDAlerte() =
        assertEquals(emptyList(), PlacementCible.alertes(listOf(c("C1", 0.0), c("C2", 60.0), c("C3", 200.0))))

    @Test fun ecartAngulairePasseParLeNord() {
        // 350 et 10 : 20 deg d'écart, pas 340
        val a = PlacementCible.alertes(listOf(c("C1", 350.0), c("C2", 10.0), c("C3", 180.0)))
        assertEquals(listOf(AlerteCible(TypeAlerte.ECART_ANGULAIRE, listOf("C1", "C2"))), a)
    }

    @Test fun ecartAngulaireDansLesDeuxSens() {
        // C1 après C2 en azimut : 90 - 40 = 50 deg (< 60) ; avec C3 à 250 : 160 et 150 deg, pas d'autre alerte
        val a = PlacementCible.alertes(listOf(c("C1", 90.0), c("C2", 40.0), c("C3", 250.0)))
        assertEquals(listOf(AlerteCible(TypeAlerte.ECART_ANGULAIRE, listOf("C1", "C2"))), a)
    }

    @Test fun ecartAngulaireIndependantDesDistances() =
        assertEquals(listOf(TypeAlerte.ECART_ANGULAIRE),
            types(listOf(c("C1", 10.0, 10.0), c("C2", 20.0, 14.0), c("C3", 200.0))))

    @Test fun ciblePlusProcheQueLaDistanceMinimale() {
        val a = PlacementCible.alertes(listOf(c("C1", 0.0, 7.99), c("C2", 120.0), c("C3", 240.0)))
        assertEquals(listOf(AlerteCible(TypeAlerte.TROP_PROCHE_DU_CENTRE, listOf("C1"))), a)
    }

    @Test fun distanceExactement8_pasDAlerte() =
        assertEquals(emptyList(), PlacementCible.alertes(listOf(c("C1", 0.0, 8.0), c("C2", 120.0), c("C3", 240.0))))

    @Test fun moinsDeTroisCibles() {
        val deux = PlacementCible.alertes(listOf(c("C1", 0.0), c("C2", 120.0)))
        assertEquals(listOf(AlerteCible(TypeAlerte.MOINS_DE_TROIS_CIBLES, emptyList())), deux)
        assertEquals(listOf(TypeAlerte.MOINS_DE_TROIS_CIBLES), types(listOf(c("C1", 0.0))))
        assertEquals(listOf(TypeAlerte.MOINS_DE_TROIS_CIBLES), types(emptyList()))
    }

    @Test fun alertesCumulees() {
        val t = types(listOf(c("C1", 0.0, 5.0), c("C2", 30.0)))
        assertEquals(setOf(TypeAlerte.ECART_ANGULAIRE, TypeAlerte.TROP_PROCHE_DU_CENTRE, TypeAlerte.MOINS_DE_TROIS_CIBLES), t.toSet())
        assertEquals(3, t.size)
    }

    @Test fun uneAlerteNEmpecheJamaisLaSaisie() {
        // une cible à 5 m (alerte) et à 30 deg d'une autre reste une saisie valide et est placée
        PlacementCible.controler("C2", 30.0, "deg", 5.0, 25.0)
        val l = PlacementCible.placer(listOf(c("C1", 0.0)), c("C2", 30.0, 5.0))
        assertEquals(listOf("C1", "C2"), l.map { it.id })
    }

    @Test fun replacerUneCibleRemplaceLAncienne() {
        val avant = listOf(c("C1", 0.0, 10.0), c("C2", 120.0, 12.0), c("C3", 240.0, 9.0))
        val apres = PlacementCible.placer(avant, CiblePlacee("C2", 130.0, 11.0, 41.0, SourceAzimut.BOUSSOLE_TELEPHONE))
        assertEquals(3, apres.size)
        assertEquals(listOf("C1", "C2", "C3"), apres.map { it.id })
        val c2 = apres.single { it.id == "C2" }
        assertEquals(130.0, c2.azimutDeg, 0.0)
        assertEquals(11.0, c2.distanceM, 0.0)
        assertEquals(41.0, c2.dbhCm, 0.0)
        assertEquals(SourceAzimut.BOUSSOLE_TELEPHONE, c2.source)
        assertTrue(apres.none { it.azimutDeg == 120.0 }, "l'ancienne mesure ne doit pas être gardée")
        assertEquals(avant[0], apres[0]); assertEquals(avant[2], apres[2])
        assertEquals(120.0, avant[1].azimutDeg, 0.0) // la liste d'origine n'est pas modifiée
    }

    @Test fun placerUneNouvelleCibleAjoute() {
        val l = PlacementCible.placer(listOf(c("C1", 0.0)), c("C3", 240.0))
        assertEquals(listOf("C1", "C3"), l.map { it.id })
    }

    @Test fun replacerEffaceLesAlertesDeLAncienne() {
        val avant = listOf(c("C1", 0.0), c("C2", 30.0), c("C3", 200.0))
        assertEquals(listOf(TypeAlerte.ECART_ANGULAIRE), types(avant))
        val apres = PlacementCible.placer(avant, c("C2", 120.0))
        assertEquals(emptyList(), PlacementCible.alertes(apres))
    }
}
