package groma.core

import groma.core.Fixtures.d
import groma.core.Fixtures.s
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class HouppiersTest {
    @Test
    fun casCommuns() {
        val c = Fixtures.lire(Fixtures.dossier("hauteurs_houppiers").resolve("cas.json"))
        val hs = c["houppiers"]!!.jsonArray.map {
            val h = it.jsonObject
            Houppier(h["houppier_id"]!!.jsonPrimitive.content.toInt(), Houppiers.anneauWkt(h["wkt"].s()!!), h["h_max"].d())
        }
        for (a in c["arbres"]!!.jsonArray.map { it.jsonObject }) {
            val r = Houppiers.affecter(a["x"].d()!!, a["y"].d()!!, hs, a["h_m"].d(), a["h_source"].s())
            val att = a["attendu"]!!.jsonObject
            val id = att["houppier_id"].let { if (it is JsonNull) null else it!!.jsonPrimitive.intOrNull }
            assertEquals(Triple(att["h_m"].d(), att["h_source"].s(), id), Triple(r.hM, r.hSource, r.houppierId), a["id"].s())
        }
    }
}
