package groma.core

import groma.core.Fixtures.d
import groma.core.Fixtures.s
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NumerotationTest {
    @TestFactory
    fun casCommuns(): List<DynamicTest> {
        val cas = Fixtures.dossier("numerotation").listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue(cas.size >= 10)
        return cas.map { f ->
            DynamicTest.dynamicTest(f.nameWithoutExtension) {
                val c = Fixtures.lire(f)
                val p = c["parametres"]!!.jsonObject
                val params = ParametresNumerotation(p["rayon_m"].d()!!, p["seuil_diam_cm"].d()!!, p["unite_azimut"].s()!!, p["tolerance_deg"].d()!!)
                val arbres = c["arbres"]!!.jsonArray.map {
                    val a = it.jsonObject
                    ArbreANumeroter(a["id"].s()!!, a["x"].d()!!, a["y"].d()!!, a["dbh_cm"].d()!!)
                }
                val r = Numerotation.numeroter(arbres, params)
                val att = c["attendu"]!!.jsonObject
                assertEquals(att["numeros"]!!.jsonObject.mapValues { it.value.s()!! }, r.numeros)
                assertEquals(att["exclus"]!!.jsonObject.mapValues { it.value.s()!! }, r.exclus)
                att["visibles"]?.let { v -> assertEquals(v.jsonArray.map { it.s()!! }.toSet(), r.visibles) }
                (att["azimuts"] as? JsonObject)?.forEach { (k, v) -> assertEquals(v.d()!!, r.azimuts.getValue(k), 1e-4) }
            }
        }
    }

    @Test
    fun doublons() {
        assertFailsWith<IllegalArgumentException> {
            Numerotation.numeroter(listOf(ArbreANumeroter("A", 1.0, 1.0, 20.0), ArbreANumeroter("A", 2.0, 2.0, 20.0)))
        }
    }

    @Test
    fun convergence() {
        val (x, y) = Numerotation.offsetsGeographiques(0.0, 10.0, 2.0)
        assertEquals(2.0, Azimut.azimut(x, y), 1e-9)
        assertEquals(-2.17682, Azimut.geographiqueVersGrille(0.0, Azimut.convergenceLambert93(6.0)) - 360.0, 1e-4)
    }
}
